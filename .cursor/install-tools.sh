#!/usr/bin/env bash
# 知家 Cloud Agent 环境 —— 系统工具链（install 阶段）
#
# 幂等、非交互、执行完即退出。把 JDK 25、Node 24、Docker 装进 /usr/local/bin，
# 让默认 PATH 能直接找到，而不只改 ~/.bashrc。
# 不启动 dockerd、Postgres 或开发服务；长驻进程属于 start。
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive
export NEEDRESTART_MODE=a
export APT_LISTCHANGES_FRONTEND=none
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

if [ "$(id -u)" -eq 0 ]; then
  as_root() { "$@"; }
else
  as_root() { sudo -n "$@"; }
fi

NODE_VERSION="24.22.3"
JDK_LABEL="Temurin-25.0.5+8"
DOCKER_VERSION="29.9.3"
COMPOSE_VERSION="5.7.1"

case "$(uname -m)" in
  x86_64)
    NODE_DIST="node-v${NODE_VERSION}-linux-x64.tar.gz"
    NODE_SHA="061ec3e3062115705b56b51fa9cc330ab50d34b6969387d4e4b1a50c6809acc2"
    JDK_DIST="OpenJDK25U-jdk_x64_linux_hotspot_25.0.5_8.tar.gz"
    JDK_SHA="68982529869257d0ee6b824ec5aa270ff8b118bb438670da0b97ebdab178d7fb"
    DOCKER_ARCH="x86_64"
    DOCKER_SHA="3fb5d1f13014c18cc54945bc177c58a9056a482dada031e17d12cf2e6922c973"
    COMPOSE_ARCH="x86_64"
    COMPOSE_SHA="af426722a8af5de812fcee30cc1c39f0639f5f723cbadefb25e5d4db054064cf"
    ;;
  aarch64)
    NODE_DIST="node-v${NODE_VERSION}-linux-arm64.tar.gz"
    NODE_SHA="261af2af250dfd7e9f25cf151d1b2713a0adf8f10a9ad116c4947f68627affe8"
    JDK_DIST="OpenJDK25U-jdk_aarch64_linux_hotspot_25.0.5_8.tar.gz"
    JDK_SHA="bb5b2ded252f21c6090cf780b0a408ad0d83bbb557e702955643557f13231880"
    DOCKER_ARCH="aarch64"
    DOCKER_SHA="1b27fd739378ef938401e81ce5afb73b7ad7432d4f598814a9ff5f826bb464d2"
    COMPOSE_ARCH="aarch64"
    COMPOSE_SHA="f2f49f903240895fa764c0dca3a14548b754ad9426f6b609ccaf55ec5a3aa2a5"
    ;;
  *)
    printf 'unsupported architecture: %s\n' "$(uname -m)" >&2
    exit 1
    ;;
esac

NODE_URL="https://nodejs.org/dist/v${NODE_VERSION}/${NODE_DIST}"
JDK_URL="https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5%2B8/${JDK_DIST}"
DOCKER_URL="https://download.docker.com/linux/static/stable/${DOCKER_ARCH}/docker-${DOCKER_VERSION}.tgz"
COMPOSE_URL="https://github.com/docker/compose/releases/download/v${COMPOSE_VERSION}/docker-compose-linux-${COMPOSE_ARCH}"

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

download() {
  local url="$1" dest="$2" sha="$3" actual
  curl --retry 5 --retry-delay 2 --retry-all-errors --retry-connrefused -fsSL "$url" -o "$dest"
  actual="$(sha256sum "$dest" | awk '{print $1}')"
  if [ "$actual" != "$sha" ]; then
    printf 'checksum mismatch for %s\nexpected %s\nactual   %s\n' "$url" "$sha" "$actual" >&2
    exit 1
  fi
}

archive_root() {
  local archive="$1" top
  top="$(tar -tzf "$archive" | awk -F/ 'NR==1 {print $1}')"
  case "$top" in
    ""|.*|*/*|*..*)
      printf 'unexpected archive root: %s\n' "$top" >&2
      exit 1
      ;;
  esac
  printf '%s\n' "$top"
}

link_bins() {
  local dir="$1" bin name
  for bin in "$dir"/*; do
    [ -f "$bin" ] || [ -L "$bin" ] || continue
    name="$(basename "$bin")"
    as_root ln -sfn "$bin" "/usr/local/bin/${name}"
  done
}

assert_on_default_path() {
  local name="$1" got
  got="$(command -v "$name" || true)"
  if [ "$got" != "/usr/local/bin/${name}" ]; then
    printf 'expected /usr/local/bin/%s on default PATH, found %s\n' "$name" "${got:-<missing>}" >&2
    exit 1
  fi
}

ensure_packages() {
  local pkg missing=()
  for pkg in "$@"; do
    if ! dpkg -s "$pkg" >/dev/null 2>&1; then
      missing+=("$pkg")
    fi
  done
  if [ "${#missing[@]}" -eq 0 ]; then
    return 0
  fi
  as_root apt-get update
  as_root apt-get install -y --no-install-recommends \
    -o Dpkg::Options::=--force-confdef \
    -o Dpkg::Options::=--force-confold \
    "${missing[@]}"
}

use_iptables_legacy() {
  local current
  if [ ! -x /usr/sbin/iptables-legacy ] || [ ! -x /usr/sbin/ip6tables-legacy ]; then
    echo "iptables-legacy is missing after package install" >&2
    exit 1
  fi
  current="$(update-alternatives --query iptables 2>/dev/null | awk '/^Value:/ {print $2; exit}' || true)"
  if [ "$current" != "/usr/sbin/iptables-legacy" ]; then
    as_root update-alternatives --set iptables /usr/sbin/iptables-legacy
    as_root update-alternatives --set ip6tables /usr/sbin/ip6tables-legacy
  fi
}

ensure_docker_daemon_config() {
  local tmp
  if [ -f /etc/docker/daemon.json ]; then
    return 0
  fi
  as_root mkdir -p /etc/docker
  tmp="${WORKDIR}/daemon.json"
  cat > "$tmp" <<'EOF'
{
  "storage-driver": "fuse-overlayfs"
}
EOF
  as_root cp "$tmp" /etc/docker/daemon.json
  as_root chmod 644 /etc/docker/daemon.json
}

ensure_docker_group() {
  local run_user
  run_user="$(id -un)"
  as_root groupadd -f docker
  if [ "$run_user" != "root" ]; then
    as_root usermod -aG docker "$run_user"
  fi
  if [ "$run_user" != "ubuntu" ] && id ubuntu >/dev/null 2>&1; then
    as_root usermod -aG docker ubuntu
  fi
}

output_has() {
  local needle="$1"
  shift
  local out
  out="$("$@" 2>&1 || true)"
  case "$out" in
    *"$needle"*) return 0 ;;
    *) return 1 ;;
  esac
}

java_ready() {
  [ -x /usr/local/lib/jdk-25/bin/java ] && [ -x /usr/local/bin/java ] \
    && output_has "$JDK_LABEL" /usr/local/lib/jdk-25/bin/java -version \
    && output_has "$JDK_LABEL" /usr/local/bin/java -version
}

install_jdk() {
  local archive top
  if java_ready; then
    echo "install-tools: jdk ${JDK_LABEL} already present"
    return 0
  fi
  archive="${WORKDIR}/jdk.tar.gz"
  download "$JDK_URL" "$archive" "$JDK_SHA"
  top="$(archive_root "$archive")"
  as_root rm -rf "/usr/local/lib/${top}"
  as_root tar -xzf "$archive" -C /usr/local/lib
  as_root ln -sfn "$top" /usr/local/lib/jdk-25
  link_bins "/usr/local/lib/${top}/bin"
  java_ready
}

node_ready() {
  [ "$(/usr/local/bin/node -v 2>/dev/null || true)" = "v${NODE_VERSION}" ]
}

install_node() {
  local archive top
  if node_ready; then
    echo "install-tools: node v${NODE_VERSION} already present"
    return 0
  fi
  archive="${WORKDIR}/node.tar.gz"
  download "$NODE_URL" "$archive" "$NODE_SHA"
  top="$(archive_root "$archive")"
  as_root rm -rf "/usr/local/lib/${top}"
  as_root tar -xzf "$archive" -C /usr/local/lib
  link_bins "/usr/local/lib/${top}/bin"
  node_ready
}

docker_ready() {
  [ -x /usr/local/bin/docker ] && [ -x /usr/local/bin/dockerd ] \
    && output_has "$DOCKER_VERSION" /usr/local/bin/docker --version \
    && output_has "$DOCKER_VERSION" /usr/local/bin/dockerd --version
}

compose_ready() {
  [ -x /usr/local/lib/docker/cli-plugins/docker-compose ] \
    && output_has "$COMPOSE_VERSION" /usr/local/bin/docker compose version
}

install_docker() {
  local archive bin
  if ! docker_ready; then
    archive="${WORKDIR}/docker.tgz"
    download "$DOCKER_URL" "$archive" "$DOCKER_SHA"
    rm -rf "${WORKDIR}/docker"
    tar -xzf "$archive" -C "$WORKDIR"
    for bin in "${WORKDIR}/docker/"*; do
      as_root install -m 0755 "$bin" "/usr/local/bin/$(basename "$bin")"
    done
  else
    echo "install-tools: docker ${DOCKER_VERSION} already present"
  fi
  if ! compose_ready; then
    download "$COMPOSE_URL" "${WORKDIR}/docker-compose" "$COMPOSE_SHA"
    as_root install -d -m 0755 /usr/local/lib/docker/cli-plugins
    as_root install -m 0755 "${WORKDIR}/docker-compose" /usr/local/lib/docker/cli-plugins/docker-compose
  else
    echo "install-tools: docker compose ${COMPOSE_VERSION} already present"
  fi
  docker_ready && compose_ready
}

# 解压与 dockerd 运行所需的包。make / tmux 是已有 start.sh 要用的，放在这里装，
# start 里不再装包。已安装则跳过 apt-get update。
ensure_packages ca-certificates curl tar gzip fuse-overlayfs iptables iproute2 make tmux
use_iptables_legacy
ensure_docker_daemon_config
ensure_docker_group

install_jdk
install_node
install_docker

# 基础镜像的登录 shell 会 source nvm，把 Node 22 插到 PATH 前面。
# 二进制仍在 /usr/local/bin；这里只在启动文件末尾把该目录重新放到前面，
# 这样 start.sh 里的 bash -lc 才会用到 Node 24。已有标记则跳过。
prefer_usr_local_bin() {
  local rc="$1" marker tmp
  marker="# zija-cloud-tools: prefer /usr/local/bin"
  if [ -f "$rc" ] && grep -F -q "$marker" "$rc"; then
    return 0
  fi
  as_root mkdir -p "$(dirname "$rc")"
  tmp="${WORKDIR}/path-snippet"
  cat > "$tmp" <<'EOF'

# zija-cloud-tools: prefer /usr/local/bin
# JDK 25、Node 24、Docker 安装在这里。放在 nvm 之后，避免登录 shell 仍指向 Node 22。
export PATH="/usr/local/sbin:/usr/local/bin:${PATH}"
export JAVA_HOME="/usr/local/lib/jdk-25"
EOF
  as_root tee -a "$rc" < "$tmp" >/dev/null
}

login_home="$(getent passwd ubuntu | cut -d: -f6 || true)"
if [ -z "${login_home}" ]; then
  login_home="${HOME}"
fi
prefer_usr_local_bin "${login_home}/.bashrc"
prefer_usr_local_bin "${login_home}/.profile"

hash -r
for name in java javac node npm docker dockerd containerd runc; do
  assert_on_default_path "$name"
done
java_ready
node_ready
docker_ready
compose_ready
/usr/local/bin/npm -v >/dev/null

echo "install-tools: $(/usr/local/bin/java -version 2>&1 | awk 'NR==1 {print}')"
echo "install-tools: node $(/usr/local/bin/node -v), npm $(/usr/local/bin/npm -v)"
echo "install-tools: $(/usr/local/bin/docker --version)"
echo "install-tools: $(/usr/local/bin/docker compose version)"
echo "install-tools: done"
