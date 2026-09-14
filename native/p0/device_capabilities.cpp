#include <dlfcn.h>
#include <sys/auxv.h>
#include <unistd.h>
#include <cstdio>
#include <cstring>
#include <cstdint>

// Android shell-process probe, NOT proof of a regular app's linker permissions.
using cl_int = int32_t;
using cl_uint = uint32_t;
using cl_platform_id = void*;
using GetPlatforms = cl_int (*)(cl_uint, cl_platform_id*, cl_uint*);
using GetPlatformInfo = cl_int (*)(cl_platform_id, cl_uint, size_t, void*, size_t*);

int main() {
    std::printf("scope=adb-shell-native-process\n");
    std::printf("page_size=%ld\n", sysconf(_SC_PAGESIZE));
    unsigned long caps = getauxval(AT_HWCAP);
    std::printf("hwcap=0x%lx\n", caps);
    // Linux AArch64 UAPI bits: ASIMD=1, FPHP=9, ASIMDHP=10, ASIMDDP=20.
    std::printf("asimd=%d fphp=%d asimdhp=%d asimddp=%d\n",
        bool(caps & (1ul<<1)), bool(caps & (1ul<<9)),
        bool(caps & (1ul<<10)), bool(caps & (1ul<<20)));
    void* lib = dlopen("libOpenCL.so", RTLD_NOW | RTLD_LOCAL);
    if (!lib) {
        const char* error = dlerror();
        std::printf("opencl_dlopen=unavailable error=%s\n", error ? error : "unknown");
        return 0;
    }
    auto getPlatforms = reinterpret_cast<GetPlatforms>(dlsym(lib,"clGetPlatformIDs"));
    auto getInfo = reinterpret_cast<GetPlatformInfo>(dlsym(lib,"clGetPlatformInfo"));
    if (!getPlatforms || !getInfo) {
        std::printf("opencl_symbols=missing\n"); dlclose(lib); return 0;
    }
    cl_uint count = 0;
    cl_int result = getPlatforms(0,nullptr,&count);
    std::printf("opencl_platform_query=%d count=%u\n", result,count);
    if (result == 0 && count > 0 && count <= 16) {
        cl_platform_id platforms[16]{};
        if (getPlatforms(count,platforms,nullptr) == 0) {
            for (cl_uint i=0;i<count;++i) {
                char name[256]{};
                auto status=getInfo(platforms[i],0x0902,sizeof(name)-1,name,nullptr); // CL_PLATFORM_NAME
                std::printf("opencl_platform_%u_status=%d name=%s\n",i,status,name);
            }
        }
    }
    dlclose(lib);
    return 0;
}
