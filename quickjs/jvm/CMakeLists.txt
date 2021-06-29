cmake_minimum_required(VERSION 3.4.1)
project(quickjs)

set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 11)

find_package(JNI)
include_directories(${JNI_INCLUDE_DIRS})

file(GLOB_RECURSE sources "../common/native/*.c" "../common/native/*.cpp")

add_library(quickjs SHARED ${sources})
target_compile_definitions(quickjs PUBLIC CONFIG_VERSION="${QUICKJS_VERSION}")

target_link_libraries(quickjs)
