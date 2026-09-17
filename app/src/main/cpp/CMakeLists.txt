cmake_minimum_required(VERSION 3.22.1)
project("revenge")

add_library(revenge SHARED
    native_bridge.cpp
    story_engine.cpp
)

find_library(log-lib log)

target_link_libraries(revenge
    ${log-lib}
)
