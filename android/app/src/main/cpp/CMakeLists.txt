cmake_minimum_required(VERSION 3.22.1)

project("voice_loopback")

# Find the Oboe library from the prefab dependency
find_package(oboe REQUIRED CONFIG)

add_library(
        native-lib
        SHARED
        native-lib.cpp)

# Link Oboe and Android libraries
target_link_libraries(
        native-lib
        oboe::oboe
        log)