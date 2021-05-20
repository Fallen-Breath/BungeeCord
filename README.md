A custom fork of [Bungeecord](https://github.com/SpigotMC/BungeeCord)

A list of small tweaks I made:

- Adjust field position in class ServerPing to make the ping response more vanilla-like
- Fix #2968: Dimension change screen doesn't show when switching from >=1.16 server to <1.16 server within the same dimension
- Added proxy setting for authenticating player with `sessionserver.mojang.com`
  - The proxy setting is the `auth_proxy` value in `config.yml` of bungee, of course you know how to fill it
  - Supported proxy types: `socks4`, `socks5`, `http`
  - If enabled, bungee will firstly try authenticating with the given proxy, if failed it will try again without the proxy 
