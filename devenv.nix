{ pkgs, lib, ... }:

let
  # One JDK per Minecraft era this repo targets. Gradle picks the right one per subproject via toolchains;
  # gradle.properties points toolchain discovery at the JDK* env vars below
  # (org.gradle.java.installations.fromEnv), so Gradle uses these instead of downloading generic JDKs.
  jdk8 = pkgs.jdk8;   # :mcheli-1.7.10 reference (RetroFuturaGradle, Forge 1.7.10)
  jdk21 = pkgs.jdk21; # :neoforge-1.21.1 + :mcheli-agnostic
  jdk25 = pkgs.jdk25; # Fabric 26.2 port

  # Native libraries LWJGL/GLFW/OpenAL dlopen at runtime for runClient (Minecraft ships its own natives,
  # but they link against these system libs, which NixOS does not provide in a global /usr/lib).
  runtimeLibs = with pkgs; [
    libGL
    vulkan-loader # 26.x tries a Vulkan graphics backend first (falls back to OpenGL without it)
    glfw3-minecraft
    openal
    libpulseaudio
    alsa-lib
    flite # vanilla narrator
    udev
    libxkbcommon
    wayland
    xorg.libX11
    xorg.libXext
    xorg.libXcursor
    xorg.libXrandr
    xorg.libXxf86vm
    xorg.libXi
  ];
in
{
  languages.java = {
    enable = true;
    jdk.package = jdk25;
    # The checked-in ./gradlew wrapper pins Gradle; don't put a second Gradle on PATH.
    gradle.enable = false;
  };

  packages = [ pkgs.git ];

  env = {
    JDK8 = "${jdk8.home}";
    JDK21 = "${jdk21.home}";
    JDK25 = "${jdk25.home}";
    LD_LIBRARY_PATH = lib.makeLibraryPath runtimeLibs;
  };

  scripts = {
    test-agnostic.exec = ''./gradlew :mcheli-agnostic:test "$@"'';
    run-neoforge-client.exec = ''./gradlew :neoforge-1.21.1:runClient "$@"'';
    run-neoforge-server.exec = ''./gradlew :neoforge-1.21.1:runServer "$@"'';
  };

  enterShell = ''
    echo "MCHeli dev shell: JDK8=$JDK8 JDK21=$JDK21 JDK25=$JDK25"
  '';

  enterTest = ''
    ./gradlew :mcheli-agnostic:test
  '';
}
