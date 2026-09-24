#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <algorithm>
#include <cmath>
#include <fstream>

#define CGLTF_IMPLEMENTATION
#include "cgltf.h"

#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"

#include "meshoptimizer.h"
#include "tiny_obj_loader.h"
#include "xatlas.h"

#include <assimp/Importer.hpp>
#include <assimp/Exporter.hpp>
#include <assimp/scene.h>
#include <assimp/postprocess.h>
#include <assimp/version.h>

namespace {

std::string fromJString(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string out = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return out;
}

jstring toJString(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

std::string errorJson(const char* tool, const std::string& message) {
    std::ostringstream o;
    o << "{\"ok\":false,\"tool\":\"" << tool << "\",\"error\":\"";
    for (char c : message) {
        if (c == '\"' || c == '\\') o << '\\';
        if (c == '\n') o << "\\n";
        else o << c;
    }
    o << "\"}";
    return o.str();
}

struct MeshData {
    std::vector<float> positions;
    std::vector<unsigned int> indices;
    size_t vertexCount = 0;
};

bool loadFirstTrianglePrimitive(const std::string& path, MeshData& out, std::string& error) {
    cgltf_options options = {};
    cgltf_data* data = nullptr;
    cgltf_result parse = cgltf_parse_file(&options, path.c_str(), &data);
    if (parse != cgltf_result_success || !data) {
        error = "cgltf parse failed: " + std::to_string((int)parse);
        return false;
    }

    cgltf_result buffers = cgltf_load_buffers(&options, data, path.c_str());
    if (buffers != cgltf_result_success) {
        error = "cgltf buffer load failed: " + std::to_string((int)buffers);
        cgltf_free(data);
        return false;
    }

    const cgltf_primitive* primitive = nullptr;
    const cgltf_accessor* positions = nullptr;
    for (cgltf_size mi = 0; mi < data->meshes_count && !primitive; ++mi) {
        const cgltf_mesh& mesh = data->meshes[mi];
        for (cgltf_size pi = 0; pi < mesh.primitives_count && !primitive; ++pi) {
            const cgltf_primitive& candidate = mesh.primitives[pi];
            if (candidate.type != cgltf_primitive_type_triangles) continue;
            for (cgltf_size ai = 0; ai < candidate.attributes_count; ++ai) {
                if (candidate.attributes[ai].type == cgltf_attribute_type_position) {
                    primitive = &candidate;
                    positions = candidate.attributes[ai].data;
                    break;
                }
            }
        }
    }

    if (!primitive || !positions || positions->count < 3) {
        error = "No triangle primitive with POSITION accessor";
        cgltf_free(data);
        return false;
    }

    out.vertexCount = (size_t)positions->count;
    out.positions.resize(out.vertexCount * 3);
    cgltf_size unpacked = cgltf_accessor_unpack_floats(
        positions, out.positions.data(), (cgltf_size)out.positions.size());
    if (unpacked < out.vertexCount * 3) {
        error = "POSITION accessor could not be fully unpacked";
        cgltf_free(data);
        return false;
    }

    if (primitive->indices) {
        out.indices.resize((size_t)primitive->indices->count);
        for (cgltf_size i = 0; i < primitive->indices->count; ++i) {
            out.indices[(size_t)i] = (unsigned int)cgltf_accessor_read_index(primitive->indices, i);
        }
    } else {
        out.indices.resize(out.vertexCount);
        for (size_t i = 0; i < out.vertexCount; ++i) out.indices[i] = (unsigned int)i;
    }

    cgltf_free(data);
    if (out.indices.size() < 3) {
        error = "Primitive has fewer than three indices";
        return false;
    }
    return true;
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_studio_ocean_app_StudioOpenSourceTools_nativeInspectGltf(
        JNIEnv* env, jclass, jstring jpath) {
    const std::string path = fromJString(env, jpath);
    cgltf_options options = {};
    cgltf_data* data = nullptr;
    cgltf_result parse = cgltf_parse_file(&options, path.c_str(), &data);
    if (parse != cgltf_result_success || !data) {
        return toJString(env, errorJson("cgltf", "parse failed code " + std::to_string((int)parse)));
    }

    cgltf_result validation = cgltf_validate(data);
    cgltf_result buffers = cgltf_load_buffers(&options, data, path.c_str());

    size_t primitives = 0;
    size_t vertices = 0;
    size_t indices = 0;
    for (cgltf_size mi = 0; mi < data->meshes_count; ++mi) {
        const cgltf_mesh& mesh = data->meshes[mi];
        primitives += (size_t)mesh.primitives_count;
        for (cgltf_size pi = 0; pi < mesh.primitives_count; ++pi) {
            const cgltf_primitive& p = mesh.primitives[pi];
            if (p.indices) indices += (size_t)p.indices->count;
            for (cgltf_size ai = 0; ai < p.attributes_count; ++ai) {
                if (p.attributes[ai].type == cgltf_attribute_type_position && p.attributes[ai].data) {
                    vertices += (size_t)p.attributes[ai].data->count;
                    break;
                }
            }
        }
    }

    std::ostringstream o;
    o << "{\"ok\":true,\"tool\":\"cgltf\","
      << "\"valid\":" << (validation == cgltf_result_success ? "true" : "false") << ","
      << "\"validationCode\":" << (int)validation << ","
      << "\"buffersLoaded\":" << (buffers == cgltf_result_success ? "true" : "false") << ","
      << "\"nodes\":" << data->nodes_count << ","
      << "\"meshes\":" << data->meshes_count << ","
      << "\"primitives\":" << primitives << ","
      << "\"vertices\":" << vertices << ","
      << "\"indices\":" << indices << ","
      << "\"materials\":" << data->materials_count << ","
      << "\"textures\":" << data->textures_count << ","
      << "\"images\":" << data->images_count << ","
      << "\"animations\":" << data->animations_count << ","
      << "\"cameras\":" << data->cameras_count << ","
      << "\"lights\":" << data->lights_count
      << "}";
    cgltf_free(data);
    return toJString(env, o.str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_studio_ocean_app_StudioOpenSourceTools_nativeSimplifyGltf(
        JNIEnv* env, jclass, jstring jpath, jfloat jratio) {
    const std::string path = fromJString(env, jpath);
    MeshData mesh;
    std::string error;
    if (!loadFirstTrianglePrimitive(path, mesh, error)) {
        return toJString(env, errorJson("meshoptimizer", error));
    }

    float ratio = std::max(0.05f, std::min(1.0f, (float)jratio));
    size_t original = mesh.indices.size();
    size_t target = (size_t)std::floor((double)original * ratio);
    target -= target % 3;
    target = std::max((size_t)3, std::min(original, target));

    std::vector<unsigned int> simplified(original);
    float resultError = 0.0f;
    size_t result = meshopt_simplify(
        simplified.data(), mesh.indices.data(), original,
        mesh.positions.data(), mesh.vertexCount, sizeof(float) * 3,
        target, 0.02f, 0, &resultError);

    std::ostringstream o;
    o << "{\"ok\":true,\"tool\":\"meshoptimizer\","
      << "\"operation\":\"simplify\","
      << "\"vertices\":" << mesh.vertexCount << ","
      << "\"originalIndices\":" << original << ","
      << "\"targetIndices\":" << target << ","
      << "\"resultIndices\":" << result << ","
      << "\"resultTriangles\":" << result / 3 << ","
      << "\"ratio\":" << ratio << ","
      << "\"error\":" << resultError << "}";
    return toJString(env, o.str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_studio_ocean_app_StudioOpenSourceTools_nativeVertexCacheGltf(
        JNIEnv* env, jclass, jstring jpath) {
    const std::string path = fromJString(env, jpath);
    MeshData mesh;
    std::string error;
    if (!loadFirstTrianglePrimitive(path, mesh, error)) {
        return toJString(env, errorJson("meshoptimizer", error));
    }

    meshopt_VertexCacheStatistics before =
        meshopt_analyzeVertexCache(mesh.indices.data(), mesh.indices.size(), mesh.vertexCount, 16, 0, 0);
    std::vector<unsigned int> optimized(mesh.indices.size());
    meshopt_optimizeVertexCache(
        optimized.data(), mesh.indices.data(), mesh.indices.size(), mesh.vertexCount);
    meshopt_VertexCacheStatistics after =
        meshopt_analyzeVertexCache(optimized.data(), optimized.size(), mesh.vertexCount, 16, 0, 0);

    std::ostringstream o;
    o << "{\"ok\":true,\"tool\":\"meshoptimizer\","
      << "\"operation\":\"vertex-cache\","
      << "\"indices\":" << mesh.indices.size() << ","
      << "\"vertices\":" << mesh.vertexCount << ","
      << "\"acmrBefore\":" << before.acmr << ","
      << "\"acmrAfter\":" << after.acmr << ","
      << "\"atvrBefore\":" << before.atvr << ","
      << "\"atvrAfter\":" << after.atvr << "}";
    return toJString(env, o.str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_studio_ocean_app_StudioOpenSourceTools_nativeInspectObj(
        JNIEnv* env, jclass, jstring jpath) {
    const std::string path = fromJString(env, jpath);
    tinyobj::ObjReaderConfig config;
    config.triangulate = true;
    tinyobj::ObjReader reader;
    if (!reader.ParseFromFile(path, config)) {
        std::string message = reader.Error().empty() ? "OBJ parse failed" : reader.Error();
        return toJString(env, errorJson("tinyobjloader", message));
    }

    const auto& attrib = reader.GetAttrib();
    const auto& shapes = reader.GetShapes();
    const auto& materials = reader.GetMaterials();
    size_t faces = 0;
    size_t indices = 0;
    for (const auto& shape : shapes) {
        faces += shape.mesh.num_face_vertices.size();
        indices += shape.mesh.indices.size();
    }

    std::ostringstream o;
    o << "{\"ok\":true,\"tool\":\"tinyobjloader\","
      << "\"vertices\":" << attrib.vertices.size() / 3 << ","
      << "\"normals\":" << attrib.normals.size() / 3 << ","
      << "\"texcoords\":" << attrib.texcoords.size() / 2 << ","
      << "\"shapes\":" << shapes.size() << ","
      << "\"materials\":" << materials.size() << ","
      << "\"faces\":" << faces << ","
      << "\"indices\":" << indices << ","
      << "\"warning\":\"" << (reader.Warning().empty() ? "" : "present") << "\"}";
    return toJString(env, o.str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_studio_ocean_app_StudioOpenSourceTools_nativeInspectImage(
        JNIEnv* env, jclass, jstring jpath) {
    const std::string path = fromJString(env, jpath);
    int width = 0, height = 0, channels = 0;
    if (!stbi_info(path.c_str(), &width, &height, &channels)) {
        const char* reason = stbi_failure_reason();
        return toJString(env, errorJson("stb_image", reason ? reason : "unsupported image"));
    }

    std::ostringstream o;
    o << "{\"ok\":true,\"tool\":\"stb_image\","
      << "\"width\":" << width << ","
      << "\"height\":" << height << ","
      << "\"channels\":" << channels << ","
      << "\"megapixels\":" << ((double)width * (double)height / 1000000.0) << "}";
    return toJString(env, o.str());
}


extern "C" JNIEXPORT jstring JNICALL
Java_studio_ocean_app_StudioOpenSourceTools_nativeAssimpExportFormats(
        JNIEnv* env, jclass) {
    Assimp::Exporter exporter;
    std::ostringstream o;
    o << "{\"ok\":true,\"tool\":\"assimp\",\"version\":\""
      << aiGetVersionMajor() << "." << aiGetVersionMinor() << "." << aiGetVersionPatch()
      << "\",\"formats\":[";
    size_t count = exporter.GetExportFormatCount();
    for (size_t i = 0; i < count; ++i) {
        const aiExportFormatDesc* d = exporter.GetExportFormatDescription(i);
        if (!d) continue;
        if (i) o << ",";
        o << "{\"id\":\"" << d->id
          << "\",\"description\":\"" << d->description
          << "\",\"extension\":\"" << d->fileExtension << "\"}";
    }
    o << "]}";
    return toJString(env, o.str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_studio_ocean_app_StudioOpenSourceTools_nativeAssimpImportExtensions(
        JNIEnv* env, jclass) {
    Assimp::Importer importer;
    aiString extensions;
    importer.GetExtensionList(extensions);
    std::ostringstream o;
    o << "{\"ok\":true,\"tool\":\"assimp\",\"extensions\":\"";
    const char* s = extensions.C_Str();
    for (; s && *s; ++s) {
        if (*s == '\"' || *s == '\\') o << '\\';
        o << *s;
    }
    o << "\"}";
    return toJString(env, o.str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_studio_ocean_app_StudioOpenSourceTools_nativeAssimpInspect(
        JNIEnv* env, jclass, jstring jpath) {
    const std::string path = fromJString(env, jpath);
    Assimp::Importer importer;
    unsigned int flags =
        aiProcess_ValidateDataStructure |
        aiProcess_Triangulate |
        aiProcess_JoinIdenticalVertices |
        aiProcess_GenSmoothNormals |
        aiProcess_CalcTangentSpace |
        aiProcess_ImproveCacheLocality |
        aiProcess_FindInvalidData |
        aiProcess_SortByPType;
    const aiScene* scene = importer.ReadFile(path, flags);
    if (!scene) return toJString(env, errorJson("assimp", importer.GetErrorString()));

    size_t vertices = 0, faces = 0, bones = 0;
    for (unsigned int i = 0; i < scene->mNumMeshes; ++i) {
        const aiMesh* m = scene->mMeshes[i];
        if (!m) continue;
        vertices += m->mNumVertices;
        faces += m->mNumFaces;
        bones += m->mNumBones;
    }
    std::ostringstream o;
    o << "{\"ok\":true,\"tool\":\"assimp\","
      << "\"meshes\":" << scene->mNumMeshes << ","
      << "\"vertices\":" << vertices << ","
      << "\"faces\":" << faces << ","
      << "\"materials\":" << scene->mNumMaterials << ","
      << "\"textures\":" << scene->mNumTextures << ","
      << "\"animations\":" << scene->mNumAnimations << ","
      << "\"cameras\":" << scene->mNumCameras << ","
      << "\"lights\":" << scene->mNumLights << ","
      << "\"bones\":" << bones << "}";
    return toJString(env, o.str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_studio_ocean_app_StudioOpenSourceTools_nativeAssimpConvert(
        JNIEnv* env, jclass, jstring jinput, jstring joutput, jstring jformat) {
    const std::string input = fromJString(env, jinput);
    const std::string output = fromJString(env, joutput);
    const std::string format = fromJString(env, jformat);

    Assimp::Importer importer;
    unsigned int flags =
        aiProcess_ValidateDataStructure |
        aiProcess_Triangulate |
        aiProcess_JoinIdenticalVertices |
        aiProcess_GenSmoothNormals |
        aiProcess_CalcTangentSpace |
        aiProcess_ImproveCacheLocality |
        aiProcess_FindDegenerates |
        aiProcess_FindInvalidData |
        aiProcess_RemoveRedundantMaterials |
        aiProcess_SortByPType |
        aiProcess_OptimizeMeshes |
        aiProcess_OptimizeGraph;
    const aiScene* scene = importer.ReadFile(input, flags);
    if (!scene) return toJString(env, errorJson("assimp", importer.GetErrorString()));

    Assimp::Exporter exporter;
    aiReturn result = exporter.Export(scene, format.c_str(), output);
    if (result != aiReturn_SUCCESS) {
        return toJString(env, errorJson("assimp", exporter.GetErrorString()));
    }

    std::ostringstream o;
    o << "{\"ok\":true,\"tool\":\"assimp\",\"operation\":\"convert\","
      << "\"format\":\"" << format << "\",\"output\":\"";
    for (char ch : output) {
        if (ch == '\"' || ch == '\\') o << '\\';
        o << ch;
    }
    o << "\"}";
    return toJString(env, o.str());
}


extern "C" JNIEXPORT jstring JNICALL
Java_studio_ocean_app_StudioOpenSourceTools_nativeAssimpProcess(
        JNIEnv* env, jclass, jstring jinput, jstring joutput, jint jmode) {
    const std::string input = fromJString(env, jinput);
    const std::string output = fromJString(env, joutput);
    int mode = (int)jmode;

    unsigned int flags = aiProcess_ValidateDataStructure | aiProcess_Triangulate;
    const char* operation = "process";
    switch (mode) {
        case 1: operation = "triangulate"; break;
        case 2: operation = "smooth-normals"; flags |= aiProcess_GenSmoothNormals; break;
        case 3: operation = "tangents"; flags |= aiProcess_GenSmoothNormals | aiProcess_CalcTangentSpace; break;
        case 4: operation = "join-identical"; flags |= aiProcess_JoinIdenticalVertices; break;
        case 5: operation = "cache-locality"; flags |= aiProcess_JoinIdenticalVertices | aiProcess_ImproveCacheLocality; break;
        case 6: operation = "optimize-meshes"; flags |= aiProcess_JoinIdenticalVertices | aiProcess_OptimizeMeshes; break;
        case 7: operation = "optimize-graph"; flags |= aiProcess_JoinIdenticalVertices | aiProcess_OptimizeMeshes | aiProcess_OptimizeGraph; break;
        case 8: operation = "remove-redundant-materials"; flags |= aiProcess_RemoveRedundantMaterials; break;
        case 9: operation = "flip-uvs"; flags |= aiProcess_FlipUVs; break;
        case 10: operation = "repair-invalid"; flags |= aiProcess_FindDegenerates | aiProcess_FindInvalidData | aiProcess_JoinIdenticalVertices; break;
        default: return toJString(env, errorJson("assimp", "unknown process mode"));
    }

    Assimp::Importer importer;
    const aiScene* scene = importer.ReadFile(input, flags);
    if (!scene) return toJString(env, errorJson("assimp", importer.GetErrorString()));

    Assimp::Exporter exporter;
    aiReturn result = exporter.Export(scene, "glb2", output);
    if (result != aiReturn_SUCCESS) {
        return toJString(env, errorJson("assimp", exporter.GetErrorString()));
    }

    size_t vertices = 0, faces = 0;
    for (unsigned int i = 0; i < scene->mNumMeshes; ++i) {
        const aiMesh* m = scene->mMeshes[i];
        if (!m) continue;
        vertices += m->mNumVertices;
        faces += m->mNumFaces;
    }

    std::ostringstream o;
    o << "{\"ok\":true,\"tool\":\"assimp\",\"operation\":\"" << operation << "\","
      << "\"meshes\":" << scene->mNumMeshes << ",\"vertices\":" << vertices
      << ",\"faces\":" << faces << ",\"output\":\"";
    for (char ch : output) {
        if (ch == '\"' || ch == '\\') o << '\\';
        o << ch;
    }
    o << "\"}";
    return toJString(env, o.str());
}


extern "C" JNIEXPORT jstring JNICALL
Java_studio_ocean_app_StudioOpenSourceTools_nativeXatlasUvObj(
        JNIEnv* env, jclass, jstring jinput, jstring joutput) {
    const std::string input = fromJString(env, jinput);
    const std::string output = fromJString(env, joutput);
    MeshData mesh;
    std::string error;
    if (!loadFirstTrianglePrimitive(input, mesh, error)) {
        return toJString(env, errorJson("xatlas", error));
    }

    xatlas::Atlas* atlas = xatlas::Create();
    if (!atlas) return toJString(env, errorJson("xatlas", "atlas allocation failed"));

    xatlas::MeshDecl decl = {};
    decl.vertexPositionData = mesh.positions.data();
    decl.vertexCount = (uint32_t)mesh.vertexCount;
    decl.vertexPositionStride = sizeof(float) * 3;
    decl.indexData = mesh.indices.data();
    decl.indexCount = (uint32_t)mesh.indices.size();
    decl.indexFormat = xatlas::IndexFormat::UInt32;

    xatlas::AddMeshError add = xatlas::AddMesh(atlas, decl, 1);
    if (add != xatlas::AddMeshError::Success) {
        std::string msg = xatlas::StringForEnum(add);
        xatlas::Destroy(atlas);
        return toJString(env, errorJson("xatlas", msg));
    }

    xatlas::ChartOptions chart;
    chart.maxIterations = 4;
    xatlas::PackOptions pack;
    pack.resolution = 2048;
    pack.padding = 4;
    pack.bilinear = true;
    pack.rotateCharts = true;
    pack.rotateChartsToAxis = true;
    pack.bruteForce = false;
    xatlas::Generate(atlas, chart, pack);

    if (atlas->meshCount < 1 || atlas->width == 0 || atlas->height == 0) {
        xatlas::Destroy(atlas);
        return toJString(env, errorJson("xatlas", "UV generation produced no atlas"));
    }

    const xatlas::Mesh& outMesh = atlas->meshes[0];
    std::ofstream out(output, std::ios::binary | std::ios::trunc);
    if (!out) {
        xatlas::Destroy(atlas);
        return toJString(env, errorJson("xatlas", "cannot open output OBJ"));
    }

    out << "# Ocean Studio xatlas UV unwrap\n";
    for (uint32_t i = 0; i < outMesh.vertexCount; ++i) {
        const xatlas::Vertex& v = outMesh.vertexArray[i];
        size_t src = (size_t)v.xref;
        if (src >= mesh.vertexCount) {
            xatlas::Destroy(atlas);
            return toJString(env, errorJson("xatlas", "output vertex references invalid source vertex"));
        }
        out << "v " << mesh.positions[src * 3 + 0] << " "
                    << mesh.positions[src * 3 + 1] << " "
                    << mesh.positions[src * 3 + 2] << "\n";
    }
    for (uint32_t i = 0; i < outMesh.vertexCount; ++i) {
        const xatlas::Vertex& v = outMesh.vertexArray[i];
        float u = v.uv[0] / (float)atlas->width;
        float tv = 1.0f - v.uv[1] / (float)atlas->height;
        out << "vt " << u << " " << tv << "\n";
    }
    for (uint32_t i = 0; i + 2 < outMesh.indexCount; i += 3) {
        uint32_t a = outMesh.indexArray[i + 0] + 1;
        uint32_t b = outMesh.indexArray[i + 1] + 1;
        uint32_t d = outMesh.indexArray[i + 2] + 1;
        out << "f " << a << "/" << a << " "
                    << b << "/" << b << " "
                    << d << "/" << d << "\n";
    }
    out.close();

    float utilization = atlas->atlasCount > 0 && atlas->utilization ? atlas->utilization[0] : 0.0f;
    std::ostringstream json;
    json << "{\"ok\":true,\"tool\":\"xatlas\",\"operation\":\"uv-unwrap\","
         << "\"resolution\":\"" << atlas->width << "x" << atlas->height << "\","
         << "\"charts\":" << atlas->chartCount << ","
         << "\"atlasCount\":" << atlas->atlasCount << ","
         << "\"inputVertices\":" << mesh.vertexCount << ","
         << "\"outputVertices\":" << outMesh.vertexCount << ","
         << "\"indices\":" << outMesh.indexCount << ","
         << "\"utilization\":" << utilization << ","
         << "\"output\":\"";
    for (char ch : output) {
        if (ch == '\"' || ch == '\\') json << '\\';
        json << ch;
    }
    json << "\"}";
    xatlas::Destroy(atlas);
    return toJString(env, json.str());
}
