# See also
# product-mini/platforms/darwin/CMakeLists.txt
# build-scripts/runtime_lib.cmake

cmake_minimum_required(VERSION 3.4.1)

project (ziplinewamr)

set(WAMR_ROOT_DIR /Users/jwilson/Development/wasm-micro-runtime)
set(ZIPLINE_ROOT_DIR /Users/jwilson/Development/zipline)

set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 11)

set(WAMR_BUILD_PLATFORM "darwin")
set(WAMR_BUILD_TARGET "AARCH64")
set(WAMR_BUILD_INTERP 1)
set(WAMR_BUILD_FAST_INTERP 0)
set(WAMR_BUILD_AOT 0)
set(WAMR_BUILD_EXCE_HANDLING 1)
set(WAMR_BUILD_GC 1)

# https://github.com/bytecodealliance/wasm-micro-runtime/issues/3909
set(WAMR_DISABLE_STACK_HW_BOUND_CHECK 1)

set (CMAKE_SHARED_LINKER_FLAGS "-Wl,-U,_get_ext_lib_export_apis")
set (CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS}")

set (CMAKE_MACOSX_RPATH True)

include (${WAMR_ROOT_DIR}/build-scripts/runtime_lib.cmake)
set (LIBC_WASI_SOURCE ${source_all})

file(GLOB_RECURSE ZIPLINE_SOURCE
  "${ZIPLINE_ROOT_DIR}/zipline/wasm-native/*.cpp"
  "${ZIPLINE_ROOT_DIR}/zipline/wasm-native/*.h"
)

set (WAMR_RUNTIME_AND_ZIPLINE_LIB_SOURCE
  ${WAMR_RUNTIME_LIB_SOURCE}
  ${ZIPLINE_SOURCE}
)

add_library (ziplinewamr SHARED ${WAMR_RUNTIME_AND_ZIPLINE_LIB_SOURCE})

target_include_directories(ziplinewamr PRIVATE
  "${ZIPLINE_ROOT_DIR}/zipline/wasm-native/include"
  "${ZIPLINE_ROOT_DIR}/zipline/wasm-native/include/share"
  "${ZIPLINE_ROOT_DIR}/zipline/wasm-native/include/unix"
)

set_target_properties (ziplinewamr PROPERTIES OUTPUT_NAME ziplinewamr)

target_link_libraries (ziplinewamr ${LLVM_AVAILABLE_LIBS} ${UV_A_LIBS} -lm -ldl -lpthread)
