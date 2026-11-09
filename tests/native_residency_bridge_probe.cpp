// Actual JNI bridge + real patched Llm header/class; controlled engine and JNI
// tables prove bridge control flow only, never Android or actual ASR inference.
#include <jni.h>
#include "llm/llm.hpp"
#include "llmconfig.hpp"
#include <map>
#include <vector>
#include <cstring>
#include <iostream>
#include <stdexcept>
#include <thread>
#include <atomic>
#include <fstream>
using MNN::Transformer::Llm;using MNN::Transformer::LlmStatus;
static int creates,loads,releases,responses,cleanups;
static bool loadBad,responseBad,cleanupBad,cleanupThrow,allocateBad,copyBad,utfBad,pending,callbackBad;
static std::vector<int> phases;
static std::string reentry;
static int reentryPhase;
static bool nestedRan,nestedRejected,nestedPreserved;
static jstring argConfig,argWav,argLanguage,argCache;
static std::thread otherThread;
static std::atomic<bool> otherFinished{false};
static std::map<jstring,std::string> strings;
static std::map<jbyteArray,std::vector<jbyte>> arrays;
struct ControlledEngine final : Llm {
 ControlledEngine():Llm(makeConfig()){}
 static std::shared_ptr<MNN::Transformer::LlmConfig> makeConfig(){auto c=std::make_shared<MNN::Transformer::LlmConfig>();c->config_=ujson::json::parse("{}");return c;}
 ~ControlledEngine() override{++releases;}
 bool load()override{++loads;if(loadBad)return false;const_cast<MNN::Transformer::LlmContext*>(getContext())->status=LlmStatus::RUNNING;return true;}
 void response(const std::vector<int>&,std::ostream*,const char*,int)override{}
 // string overload is nonvirtual but calls tokenizer then vector overload;
 // bridge test uses a deliberately narrow response port below, not fake headers.
 bool clearRequestState()override{++cleanups;if(cleanupThrow)throw std::runtime_error("controlled cleanup throw");const_cast<MNN::Transformer::LlmContext*>(getContext())->os=nullptr;return !cleanupBad;}
};
static Llm* controlCreate(const std::string&){++creates;return new ControlledEngine;}
static void controlResponse(Llm* l,const std::string&,std::ostream* out){++responses;auto*c=const_cast<MNN::Transformer::LlmContext*>(l->getContext());c->os=out;c->status=responseBad?LlmStatus::INTERNAL_ERROR:LlmStatus::NORMAL_FINISHED;c->gen_seq_len=2;*out<<"public";}
#define LLM_ASR_ENGINE_FACTORY controlCreate
#define LLM_ASR_RESPONSE controlResponse
#include "../native/apk/asr_jni.cpp"
static jboolean JNICALL exceptionCheck(JNIEnv*){return pending?JNI_TRUE:JNI_FALSE;}
static void JNICALL exceptionClear(JNIEnv*){pending=false;}
static const char* JNICALL chars(JNIEnv*,jstring s,jboolean*){if(utfBad){pending=true;return nullptr;}return strings.at(s).c_str();}
static void JNICALL releaseChars(JNIEnv*,jstring,const char*){}
static jclass JNICALL findClass(JNIEnv*,const char*){return reinterpret_cast<jclass>(uintptr_t(8));}
static jint JNICALL throwNew(JNIEnv*,jclass,const char*){pending=true;return 0;}
static jclass JNICALL getClass(JNIEnv*,jobject){return reinterpret_cast<jclass>(uintptr_t(8));}
static jmethodID JNICALL getMethod(JNIEnv*,jclass,const char*,const char*){return reinterpret_cast<jmethodID>(uintptr_t(9));}
static void JNICALL callVoid(JNIEnv* env,jobject,jmethodID,va_list args){
 int phase=va_arg(args,int);phases.push_back(phase);if(callbackBad)pending=true;
 if(!nestedRan && phase==reentryPhase && !reentry.empty()) {
  nestedRan=true;auto before=resident.get();
  if(reentry=="thread") {
   otherThread=std::thread([env](){Java_org_llmasr_minimal_asr_JniNativeTranscription_releaseNative(env,nullptr);otherFinished=true;});return;
  }
  if(reentry=="release")Java_org_llmasr_minimal_asr_JniNativeTranscription_releaseNative(env,nullptr);
  else if(reentry=="prepare")Java_org_llmasr_minimal_asr_JniNativeTranscription_prepareNative(env,nullptr,argConfig,argCache,nullptr);
  else Java_org_llmasr_minimal_asr_JniNativeTranscription_transcribeWithListener(env,nullptr,argConfig,argWav,argLanguage,argCache,nullptr);
  nestedRejected=pending;nestedPreserved=resident.get()==before;
 }
}
static void JNICALL deleteRef(JNIEnv*,jobject){}
static jbyteArray JNICALL newBytes(JNIEnv*,jsize n){if(allocateBad){pending=true;return nullptr;}auto a=reinterpret_cast<jbyteArray>(uintptr_t(100+arrays.size()));arrays[a].resize(n);return a;}
static void JNICALL putBytes(JNIEnv*,jbyteArray a,jsize start,jsize n,const jbyte*b){if(copyBad){pending=true;return;}std::copy(b,b+n,arrays[a].begin()+start);}
static void require(bool b,const char*m){if(!b)throw std::runtime_error(m);}
int main(int argc,char**argv){try{
 require(argc==2,"ARGS");std::string scenario=argv[1];
 JNINativeInterface_ table{};table.ExceptionCheck=exceptionCheck;table.ExceptionClear=exceptionClear;table.GetStringUTFChars=chars;table.ReleaseStringUTFChars=releaseChars;table.FindClass=findClass;table.ThrowNew=throwNew;table.GetObjectClass=getClass;table.GetMethodID=getMethod;table.CallVoidMethodV=callVoid;table.DeleteLocalRef=deleteRef;table.NewByteArray=newBytes;table.SetByteArrayRegion=putBytes;JNIEnv env{&table};
 char directoryTemplate[]="/tmp/llm-asr-bridge-XXXXXX";
 char* directory=::mkdtemp(directoryTemplate);require(directory,"SYNTHETIC_GRAPH_DIRECTORY");
 const std::string graphDirectory(directory);
 struct FixtureCleanup { std::string path; ~FixtureCleanup(){for(const char* name:{"audio.mnn","llm.mnn","audio.mnn.weight","llm.mnn.weight"})::unlink((path+"/"+name).c_str());::rmdir(path.c_str());} } fixture{graphDirectory};
 MNN::NetT graph;std::unique_ptr<MNN::OpT> node(new MNN::OpT);node->type=MNN::OpType_Const;MNN::BlobT blob;blob.dataType=MNN::DataType_DT_FLOAT;blob.dims={1};blob.float32s={1.0f};node->main.Set(std::move(blob));graph.oplists.push_back(std::move(node));flatbuffers::FlatBufferBuilder graphBuilder;graphBuilder.Finish(MNN::Net::Pack(graphBuilder,&graph));
 for(const char* name:{"audio.mnn","llm.mnn"}){std::ofstream output(graphDirectory+"/"+name,std::ios::binary);output.write(reinterpret_cast<const char*>(graphBuilder.GetBufferPointer()),graphBuilder.GetSize());require(output.good(),"SYNTHETIC_GRAPH_WRITE");}
 for(const char* name:{"audio.mnn.weight","llm.mnn.weight"}){std::ofstream output(graphDirectory+"/"+name,std::ios::binary);output.put('x');require(output.good(),"SYNTHETIC_WEIGHT_WRITE");}
 auto str=[](int id,const char*v){auto s=reinterpret_cast<jstring>(uintptr_t(id));strings[s]=v;return s;};auto c=str(1,"config"),w=str(2,"wav"),lang=str(3,"auto"),cache=str(4,"cache");auto listener=reinterpret_cast<jobject>(uintptr_t(7));
 strings[c]=graphDirectory+"/config.json";
 argConfig=c;argWav=w;argLanguage=lang;argCache=cache;
 if(scenario.find("reentry-")==0){reentryPhase=scenario[8]-'0';reentry=scenario.substr(10);}
 if(scenario=="thread-serial"){reentry="thread";reentryPhase=3;}
 auto prepare=[&](){return Java_org_llmasr_minimal_asr_JniNativeTranscription_prepareNative(&env,nullptr,c,cache,listener);};
 auto request=[&](){return Java_org_llmasr_minimal_asr_JniNativeTranscription_transcribeWithListener(&env,nullptr,c,w,lang,cache,listener);};
 if(scenario=="unprepared"){require(request()==nullptr&&creates==0&&pending,"UNPREPARED_FALLBACK_LOAD");}
 else if(scenario=="loadfail"){loadBad=true;require(prepare()<0&&pending&&releases==1&&!resident,"LOAD_FAILURE_RETAINED");}
 else{
  require(prepare()>=0&&!pending&&creates==1&&loads==1&&cleanups==0,"FRESH_PREPARE_CALLED_NORMAL_CLEANUP");
  require(phases==(reentryPhase==1?std::vector<int>({1}):std::vector<int>({1,2})),"PREPARE_PHASES");phases.clear();
  if(!reentry.empty() || scenario=="legacy") {
   if(reentryPhase<=2 && !reentry.empty())require(nestedRan&&nestedRejected&&nestedPreserved&&!pending,"PREPARE_REENTRY_NOT_REJECTED_SAFELY");
   auto out=scenario=="legacy"?Java_org_llmasr_minimal_asr_JniNativeTranscription_transcribe(&env,nullptr,c,w,lang,cache):request();
   require(out&&!pending,"OUTER_REENTRY_REQUEST_FAILED");
   if(reentry=="thread") {otherThread.join();require(otherFinished&&!resident&&releases==1,"CROSS_THREAD_VALID_SERIALIZATION_FAILED");}
   else {
    if(!reentry.empty())require(nestedRan&&nestedRejected&&nestedPreserved&&resident,"NESTED_REENTRY_EVICTED_OUTER");
    reentry.clear();require(request()&&!pending&&resident&&cleanups==2,"SUBSEQUENT_REQUEST_AFTER_REENTRY_FAILED");
   }
  }else if(scenario=="normal"){
   require(prepare()==0&&creates==1,"PREPARE_NOT_IDEMPOTENT");
   for(int i=0;i<3;++i){auto a=request();require(a&&!pending&&resident,"WARM_REQUEST_EVICTED");std::string text(arrays[a].begin(),arrays[a].end());require(text.find("0 ")==0&&text.find("\npublic")!=std::string::npos,"WARM_PROTOCOL");}
   require(creates==1&&loads==1&&responses==3&&cleanups==3&&releases==0,"REQUEST_RELOADED");require(phases==std::vector<int>({3,4,3,4,3,4}),"WARM_FAKE_LOAD_PHASES");
  }else{
   allocateBad=scenario=="allocation";copyBad=scenario=="copy";utfBad=scenario=="utf";responseBad=scenario=="response";cleanupBad=scenario=="cleanup";cleanupThrow=scenario=="cleanupthrow";pending=scenario=="pending";callbackBad=scenario=="callback";
   auto out=request();
   if(callbackBad)require(out&&!pending&&resident,"OPTIONAL_CALLBACK_CHANGED_RESULT");
   else require(!out&&pending&&!resident&&releases==1,"FAILED_REQUEST_RESIDENT_RETAINED");
   if(allocateBad||copyBad)require(cleanups==1,"ALLOCATION_BEFORE_CLEANUP");
  }
 }
 Java_org_llmasr_minimal_asr_JniNativeTranscription_releaseNative(&env,nullptr);
 require(!resident,"RELEASE_RETAINED");std::cout<<"PASS actual JNI bridge controlled "<<scenario<<std::endl;return 0;
}catch(const std::exception&e){std::cerr<<"ASSERTION: "<<e.what()<<std::endl;return 2;}}
