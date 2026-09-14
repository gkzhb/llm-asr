{
  description = "Qwen3-ASR Android development tools (initial ADB environment)";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs { inherit system; };
      # Explicit SDK/NDK license acceptance for this project's Android development.
      # No shell hook accepts licenses or downloads SDK components implicitly.
      androidPkgs = import nixpkgs {
        inherit system;
        config = { allowUnfree = true; android_sdk.accept_license = true; };
      };
      ndk = (androidPkgs.androidenv.composeAndroidPackages {
        includeNDK = true;
        ndkVersions = [ "28.2.13676358" ];
        platformVersions = [ ];
        buildToolsVersions = [ ];
        includeCmake = false;
        includeEmulator = false;
        toolsVersion = null;
      }).ndk-bundle;
      sdk = (androidPkgs.androidenv.composeAndroidPackages {
        platformVersions = [ "35" ];
        buildToolsVersions = [ "35.0.0" ];
        includeNDK = false;
        includeCmake = false;
        includeEmulator = false;
        toolsVersion = null;
      }).androidsdk;
      adbShell = pkgs.mkShellNoCC {
        packages = [ pkgs.android-tools ];
        shellHook = ''
          echo "ADB environment ready. Connect explicitly with: adb connect HOST:PORT"
        '';
      };
    in {
      devShells.${system} = {
        default = adbShell;
        adb = adbShell;
        native = pkgs.mkShell {
          packages = with pkgs; [ android-tools cmake ninja pkg-config git python312 ndk ];
          ANDROID_NDK_ROOT = "${ndk}/libexec/android-sdk/ndk/28.2.13676358";
          CMAKE_BUILD_PARALLEL_LEVEL = "2";
        };
        apk = pkgs.mkShell {
          packages = with pkgs; [ android-tools jdk17 python312 zip unzip ndk sdk ];
          ANDROID_HOME = "${sdk}/libexec/android-sdk";
          ANDROID_NDK_ROOT = "${ndk}/libexec/android-sdk/ndk/28.2.13676358";
          JAVA_HOME = "${pkgs.jdk17}";
        };
        model = pkgs.mkShell {
          packages = with pkgs; [ python312 uv cmake ninja pkg-config git curl patchelf ];
          LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath [ pkgs.stdenv.cc.cc.lib pkgs.zlib pkgs.libsndfile ];
          UV_PYTHON_DOWNLOADS = "never";
          UV_PYTHON = "${pkgs.python312}/bin/python3.12";
          OMP_NUM_THREADS = "2";
          MKL_NUM_THREADS = "2";
        };
      };

      packages.${system} = {
        default = pkgs.android-tools;
        android-tools = pkgs.android-tools;
      };

      apps.${system} = {
        default = self.apps.${system}.adb;
        adb = {
          type = "app";
          program = "${pkgs.android-tools}/bin/adb";
          meta.description = "Android Debug Bridge (no automatic device connection)";
        };
      };

      checks.${system}.adb-version = pkgs.runCommand "adb-version-check" { } ''
        export HOME="$TMPDIR/adb-home"
        mkdir -p "$HOME"
        ${pkgs.android-tools}/bin/adb version > "$out"
      '';
    };
}
