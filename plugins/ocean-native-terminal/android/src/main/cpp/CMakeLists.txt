cmake_minimum_required(VERSION 3.22.1)
project(oceanpty)

add_library(oceanpty SHARED pty_jni.c)

find_library(log-lib log)

target_link_libraries(oceanpty ${log-lib})
