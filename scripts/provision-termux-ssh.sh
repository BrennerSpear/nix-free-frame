#!/data/data/com.termux/files/usr/bin/bash
# Run INSIDE Termux with a trusted-USB supplied public-key file as the only input.
set -eu
umask 077
if [ "$#" -lt 1 ] || [ "$#" -gt 2 ] || [ ! -r "$1" ]; then
  echo 'Usage (inside Termux): provision-termux-ssh.sh PUBLIC_KEYS_FILE [PORT]' >&2; exit 1
fi
port="${2:-8022}"
case "$port" in ''|*[!0-9]*) echo 'Invalid SSH port' >&2;exit 1;; esac
[ "$port" -ge 1024 ] && [ "$port" -le 65535 ] || exit 1
frame_home="$HOME"
config="$frame_home/.ssh/sshd_nix_frame.conf"
boot="$frame_home/.termux/boot/nix-frame-sshd"
if [ -e "$config" ] || [ -e "$boot" ]; then echo 'Dedicated files exist; preserve and review before provisioning.' >&2; exit 1; fi
# No passwords, network-provided commands, or mutation of upstream sshd_config.
while IFS= read -r key || [ -n "$key" ]; do
  case "$key" in ssh-ed25519\ *) ;; *) echo 'Only complete Ed25519 public-key lines accepted' >&2; exit 1;; esac
done < "$1"
[ -s "$1" ] || exit 1
properties="$frame_home/.termux/termux.properties"
if [ -e "$properties" ]; then
 count=$(grep -Ec '^[[:space:]]*allow-external-apps[[:space:]]*=' "$properties" || true)
 if [ "$count" -gt 1 ] || { [ "$count" -eq 1 ] && ! grep -Eq '^[[:space:]]*allow-external-apps[[:space:]]*=[[:space:]]*true[[:space:]]*$' "$properties"; }; then
  echo 'Existing allow-external-apps is false/ambiguous; back up and explicitly edit it before provisioning.' >&2;exit 1
 fi
fi
pkg install -y openssh
while IFS= read -r key || [ -n "$key" ]; do
  printf '%s\n' "$key" | ssh-keygen -lf - >/dev/null || exit 1
done < "$1"
mkdir -p "$frame_home/.ssh" "$frame_home/.termux/boot"
chmod 700 "$frame_home/.ssh" "$frame_home/.termux/boot"
auth="$frame_home/.ssh/authorized_keys"
if [ -e "$auth" ]; then cp -p "$auth" "$frame_home/.ssh/authorized_keys-before-nix-frame-$(date +%s)"; fi
while IFS= read -r key || [ -n "$key" ]; do
  if ! [ -f "$auth" ] || ! grep -Fqx "$key" "$auth"; then printf '%s\n' "$key" >> "$auth"; fi
done < "$1"
chmod 600 "$auth"
ssh-keygen -A
frame_user=$(id -un)
set -C
cat > "$config" <<CONFIG
Port $port
ListenAddress 0.0.0.0
HostKey $PREFIX/etc/ssh/ssh_host_ed25519_key
PidFile $frame_home/.ssh/nix-frame-sshd.pid
AuthorizedKeysFile $auth
PubkeyAuthentication yes
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitEmptyPasswords no
PermitRootLogin no
AllowUsers $frame_user
AllowTcpForwarding no
AllowAgentForwarding no
X11Forwarding no
PermitTunnel no
Subsystem sftp $PREFIX/libexec/sftp-server
CONFIG
sshd -t -f "$config"
cat > "$boot" <<'BOOT'
#!/data/data/com.termux/files/usr/bin/bash
set -eu
config="$HOME/.ssh/sshd_nix_frame.conf"
pidfile="$HOME/.ssh/nix-frame-sshd.pid"
if [ -f "$pidfile" ]; then
 pid=$(cat "$pidfile")
 case "$pid" in ''|*[!0-9]*) exit 1;; esac
 if kill -0 "$pid" 2>/dev/null; then
  uid=$(stat -c %u "/proc/$pid")
  cmd=$(tr '\0' ' ' < "/proc/$pid/cmdline")
  if [ "$uid" = "$(id -u)" ] && [ "$cmd" = "$PREFIX/bin/sshd -D -f $config " ]; then exit 0; fi
  echo 'Dedicated PID belongs to an unexpected process; refusing duplicate daemon.' >&2; exit 1
 fi
fi
termux-wake-lock
exec "$PREFIX/bin/sshd" -D -f "$config"
BOOT
chmod 700 "$boot"
# Preserve settings; append property only when absent, never create divergent duplicate.
properties="$frame_home/.termux/termux.properties"
if [ -e "$properties" ]; then cp -p "$properties" "$frame_home/.ssh/termux-properties-before-nix-frame-$(date +%s)"; fi
if grep -Eq '^[[:space:]]*allow-external-apps[[:space:]]*=' "$properties" 2>/dev/null; then
 echo 'Review existing allow-external-apps setting; no duplicate appended.'
else printf '\nallow-external-apps = true\n' >> "$properties"; fi
termux-reload-settings
printf 'Configured key-only SSH for %s. Capture host key through trusted USB before connecting:\n' "$frame_user"
cat "$PREFIX/etc/ssh/ssh_host_ed25519_key.pub"
printf 'Grant ONLY the frame RUN_COMMAND permission and Termux battery exemption over trusted USB; wait 45 seconds before reboot. Start the managed script once inside Termux.\n'
