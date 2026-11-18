cmake_minimum_required(VERSION 3.22.1)
project("opensleep_sonar")

find_package(oboe REQUIRED CONFIG)

add_library(
    opensleep_sonar
    SHARED
    sonar_engine.cpp
    sonar_jni.cpp
)

target_link_libraries(
    opensleep_sonar
    oboe::oboe
    log
    m
)
