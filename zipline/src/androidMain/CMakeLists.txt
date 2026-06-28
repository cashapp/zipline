cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 11)

set(MI_OVERRIDE OFF)
set(MI_BUILD_STATIC ON)
set(MI_BUILD_SHARED OFF)
set(MI_BUILD_OBJECT OFF)
set(MI_BUILD_TESTS OFF)
add_subdirectory("../../mimalloc" mimalloc)
target_link_options(mimalloc-static PRIVATE "-Wl,-z,max-page-size=16384")

file(GLOB_RECURSE sources "../../native/*.c" "../../native/*.cpp")
add_library(quickjs SHARED ${sources})
target_link_libraries(quickjs PRIVATE mimalloc-static)
# Link Android log library for __android_log_print (used by IntSetBuiltins.cpp debug logs).
target_link_libraries(quickjs PRIVATE log)
target_link_options(quickjs PRIVATE "-Wl,-z,max-page-size=16384")
