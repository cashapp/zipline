cmake_minimum_required(VERSION 3.4.1)

file(GLOB_RECURSE quickjs_SRC
    "*.c"
)

add_library(quickjs SHARED ${quickjs_SRC})

target_link_libraries(quickjs)
