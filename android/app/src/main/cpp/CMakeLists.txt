cmake_minimum_required(VERSION 3.22.1)
project(oceanpty LANGUAGES C CXX)

add_library(oceanpty SHARED ocean_pty.c)
target_compile_options(oceanpty PRIVATE -Wall -Wextra -Werror)
target_link_libraries(oceanpty log)

# Ocean Studio external open-source toolchain.
# Every dependency is pinned to an exact upstream GitHub commit so builds are
# reproducible and a moving branch can never silently change shipped code.
include(FetchContent)
set(FETCHCONTENT_QUIET OFF)

FetchContent_Declare(
  cgltf
  URL https://github.com/jkuhlmann/cgltf/archive/360db1a95480fe102ae9c69b27c5d101167ff5ba.tar.gz
)
FetchContent_GetProperties(cgltf)
if(NOT cgltf_POPULATED)
  FetchContent_Populate(cgltf)
endif()

FetchContent_Declare(
  stb
  URL https://github.com/nothings/stb/archive/2c980bb59875b0d32144a71867fbdebb2f77cd20.tar.gz
)
FetchContent_GetProperties(stb)
if(NOT stb_POPULATED)
  FetchContent_Populate(stb)
endif()

set(MESHOPT_INSTALL OFF CACHE BOOL "" FORCE)
set(MESHOPT_BUILD_DEMO OFF CACHE BOOL "" FORCE)
set(MESHOPT_BUILD_GLTFPACK OFF CACHE BOOL "" FORCE)
FetchContent_Declare(
  meshoptimizer
  URL https://github.com/zeux/meshoptimizer/archive/9d9890c73011d75920af614485296d1e03e95448.tar.gz
)
FetchContent_MakeAvailable(meshoptimizer)

set(TINYOBJLOADER_WITH_PYTHON OFF CACHE BOOL "" FORCE)
set(TINYOBJLOADER_BUILD_TEST_LOADER OFF CACHE BOOL "" FORCE)
set(TINYOBJLOADER_BUILD_OBJ_STICHER OFF CACHE BOOL "" FORCE)
set(TINYOBJLOADER_BUILD_C_LIBRARY OFF CACHE BOOL "" FORCE)
FetchContent_Declare(
  tinyobjloader
  URL https://github.com/tinyobjloader/tinyobjloader/archive/45636bdcef1a4fec140346b90c0b50bf0bc3e23b.tar.gz
)
FetchContent_MakeAvailable(tinyobjloader)

# Assimp supplies Ocean Studio's broad format import/export bridge.
set(ASSIMP_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(ASSIMP_BUILD_ASSIMP_TOOLS OFF CACHE BOOL "" FORCE)
set(ASSIMP_BUILD_SAMPLES OFF CACHE BOOL "" FORCE)
set(ASSIMP_BUILD_ZLIB ON CACHE BOOL "" FORCE)
set(ASSIMP_INSTALL OFF CACHE BOOL "" FORCE)
set(ASSIMP_WARNINGS_AS_ERRORS OFF CACHE BOOL "" FORCE)
set(ASSIMP_BUILD_ALL_IMPORTERS_BY_DEFAULT ON CACHE BOOL "" FORCE)
set(ASSIMP_BUILD_ALL_EXPORTERS_BY_DEFAULT ON CACHE BOOL "" FORCE)
FetchContent_Declare(
  assimp
  URL https://github.com/assimp/assimp/archive/fb375dd8c0a032106a2122815fb18dffe0283721.tar.gz
)
FetchContent_MakeAvailable(assimp)

# xatlas powers real automatic UV charting/unwrapping.
FetchContent_Declare(
  xatlas_src
  URL https://github.com/jpcy/xatlas/archive/f700c7790aaa030e794b52ba7791a05c085faf0c.tar.gz
)
FetchContent_GetProperties(xatlas_src)
if(NOT xatlas_src_POPULATED)
  FetchContent_Populate(xatlas_src)
endif()
add_library(xatlas STATIC ${xatlas_src_SOURCE_DIR}/source/xatlas/xatlas.cpp)
target_include_directories(xatlas PUBLIC ${xatlas_src_SOURCE_DIR}/source/xatlas)

add_library(oceanstudio_ext SHARED studio_external_tools.cpp)
set_target_properties(oceanstudio_ext PROPERTIES CXX_STANDARD 17 CXX_STANDARD_REQUIRED ON)
target_include_directories(oceanstudio_ext PRIVATE
  ${cgltf_SOURCE_DIR}
  ${stb_SOURCE_DIR}
)
target_compile_options(oceanstudio_ext PRIVATE -Wall -Wextra)
target_link_libraries(oceanstudio_ext log meshoptimizer tinyobjloader assimp xatlas)
