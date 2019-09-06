cmake_minimum_required(VERSION 3.4.1)

file(GLOB_RECURSE sources "*.c" "*.cpp")

add_library(quickjs SHARED ${sources})

target_link_libraries(quickjs)
