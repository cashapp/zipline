cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 11)

file(GLOB_RECURSE sources "../../native/*.c" "../../native/*.cpp")

add_library(quickjs SHARED ${sources})

target_link_libraries(quickjs)
