cmake_minimum_required(VERSION 3.4.1)
project(quickjs)

set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS}")

set(JAVA_AWT_LIBRARY NotNeeded)
set(JAVA_AWT_INCLUDE_PATH NotNeeded)
find_package(JNI REQUIRED)
include_directories(${JNI_INCLUDE_DIRS})

file(GLOB_RECURSE sources "../../native/*.c" "../../native/*.cpp")

add_library(quickjs SHARED ${sources})
target_compile_definitions(quickjs PUBLIC CONFIG_VERSION="${QUICKJS_VERSION}")

target_link_libraries(quickjs)
