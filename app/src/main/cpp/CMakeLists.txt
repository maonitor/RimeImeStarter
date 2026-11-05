cmake_minimum_required(VERSION 3.22.1)
project("imebridge" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# 这个桥接库只依赖 NDK 系统库：librime 本体在运行时用 dlopen("librime.so") 加载。
# 好处：
#  1. 不链接 librime.so，所以即使 jniLibs 里暂时没有 librime.so，工程也能完整编译；
#  2. librime 可以独立升级/替换（只要导出 rime_get_api）；
#  3. 避免把 GPL 的 Trime JNI 层或整套 C++ 依赖拖进本工程。
add_library(imebridge SHARED ime_bridge.cpp)

target_include_directories(imebridge PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/third_party)
target_compile_options(imebridge PRIVATE -Wall -Wextra -fvisibility=hidden)

find_library(log-lib log)
find_library(dl-lib dl)
target_link_libraries(imebridge PRIVATE ${log-lib} ${dl-lib})
