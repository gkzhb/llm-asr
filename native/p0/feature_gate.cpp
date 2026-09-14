#include <audio/audio.hpp>
#include <MNN/expr/ExprCreator.hpp>
#include <fstream>
#include <iostream>
int main(int argc,char**argv){
    if(argc!=3)return 2;
    auto wave=MNN::AUDIO::load(argv[1],16000).first;
    if(wave == nullptr)return 3;
    auto mel=MNN::AUDIO::whisper_fbank(wave,16000,128,400,160,0);
    if(mel == nullptr || !mel->getInfo())return 4;
    auto info=mel->getInfo();auto data=mel->readMap<float>();if(!data)return 5;
    std::ofstream out(argv[2],std::ios::binary);
    out.write(reinterpret_cast<const char*>(data),info->size*sizeof(float));
    if(!out)return 6;
    std::cout<<"shape=";for(int n:info->dim)std::cout<<n<<",";
    std::cout<<" elements="<<info->size<<std::endl;
    return 0;
}
