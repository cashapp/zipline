const std = @import("std");

pub fn build(b: *std.Build) !void {
  try setupTarget(b, .linux, .aarch64, "aarch64");
  try setupTarget(b, .linux, .x86_64, "amd64");
  try setupTarget(b, .macos, .aarch64, "aarch64");
  try setupTarget(b, .macos, .x86_64, "x86_64");
  try setupTarget(b, .windows, .aarch64, "aarch64");
  try setupTarget(b, .windows, .x86_64, "amd64");
}

fn setupTarget(b: *std.Build, tag: std.Target.Os.Tag, arch: std.Target.Cpu.Arch, dir: []const u8) !void {
  const lib = b.addLibrary(.{
    .name = "quickjs",
    .linkage = .dynamic,
    .root_module = b.createModule(.{
      .target = b.resolveTargetQuery(.{
        .cpu_arch = arch,
        .os_tag = tag,
        // We need to explicitly specify gnu for linux, as otherwise it defaults to musl.
        // See https://github.com/ziglang/zig/issues/16624#issuecomment-1801175600.
        .abi = if (tag == .linux) .gnu else null,
      }),
      .optimize = .ReleaseSmall,
    }),
  });

  const version = readVersionFile();
  var quoted_version_buf: [64]u8 = undefined;
  const quoted_version = try std.fmt.bufPrint(&quoted_version_buf, "\"{s}\"", .{ version });
  lib.root_module.addCMacro("CONFIG_VERSION", quoted_version);

  lib.root_module.addIncludePath(b.path("native/include/share"));
  lib.root_module.addIncludePath(
    switch (tag) {
      .windows => b.path("native/include/windows"),
      else => b.path("native/include/unix"),
    }
  );
  lib.root_module.addIncludePath(b.path("mimalloc/include"));

  lib.root_module.link_libc = true;

  // TODO Tree-walk these two dirs for all C files.
  lib.root_module.addCSourceFiles(.{
    .files = &.{
      "mimalloc/src/alloc.c",
      "mimalloc/src/alloc-aligned.c",
      "mimalloc/src/alloc-posix.c",
      "mimalloc/src/arena.c",
      "mimalloc/src/arena-meta.c",
      "mimalloc/src/bitmap.c",
      "mimalloc/src/heap.c",
      "mimalloc/src/init.c",
      "mimalloc/src/libc.c",
      "mimalloc/src/options.c",
      "mimalloc/src/os.c",
      "mimalloc/src/page.c",
      "mimalloc/src/page-map.c",
      "mimalloc/src/random.c",
      "mimalloc/src/stats.c",
      "mimalloc/src/theap.c",
      "mimalloc/src/threadlocal.c",
      "mimalloc/src/prim/prim.c",
    },
    .flags = &.{
      "-Wno-date-time",
    },
  });
  lib.root_module.addCSourceFiles(.{
    .files = &.{
      "native/mimalloc/mimalloc-quickjs.c",
      "native/common/context-no-eval.c",
      "native/common/finalization-registry.c",
      "native/common/global-gc.c",
      "native/quickjs/cutils.c",
      "native/quickjs/libregexp.c",
      "native/quickjs/libunicode.c",
      "native/quickjs/quickjs.c",
      "native/quickjs/dtoa.c",
    },
    .flags = &.{
      "-std=gnu99",
    },
  });

  lib.root_module.link_libcpp = true;
  // TODO Tree-walk this dirs for all C++ files.
  lib.root_module.addCSourceFiles(.{
    .files = &.{
      "native/Context.cpp",
      "native/ExceptionThrowers.cpp",
      "native/InboundCallChannel.cpp",
      "native/OutboundCallChannel.cpp",
      "native/quickjs-jni.cpp",
      "native/IntSetBuiltins.cpp",
    },
    .flags = &.{
      "-std=c++11",
    },
  });

  const install = b.addInstallArtifact(lib, .{
    .dest_dir = .{
      .override = .{
        .custom = dir,
      },
    },
  });

  b.getInstallStep().dependOn(&install.step);
}

fn readVersionFile() []const u8 {
  const version = @embedFile("native/quickjs/VERSION");
  return std.mem.trim(u8, version, "\r\n");
}
