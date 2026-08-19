# IPTV Family - Windows Desktop Module Config

# This module uses Compose Multiplatform with jpackage for native installers
# Run: ./gradlew :composeApp:packageMsi (Windows) / :composeApp:packageDmg (macOS) / :composeApp:packageDeb (Linux)

# Requirements:
# - JDK 17+ with jpackage (included in JDK 14+)
# - Windows: WiX Toolset for MSI (optional, jpackage can create EXE without it)
# - Linux: dpkg-deb for .deb, rpmbuild for .rpm
# - macOS: built-in tools for .dmg