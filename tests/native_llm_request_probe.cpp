#include "module/StaticModule.hpp"
#include "core/TensorUtils.hpp"
#include "native_request_probe_support.hpp"
#include "omni.hpp"
#include "tokenizer/tokenizer.hpp"
namespace MNN {namespace Transformer {
struct RequestCleanupProbe {
 static void inject(Llm& l,std::ostream& os) {
  l.set_config(R"({"asr_language":"English"})");
  auto& c=*l.mContext;c.os=&os;c.end_with="end-public";c.generate_str="public";c.log_buffer="publiclog";
  c.prompt_len=4;c.gen_seq_len=3;c.all_seq_len=7;c.current_token=9;c.history_tokens={1,2};c.output_tokens={3};
  c.vision_us=c.audio_us=c.prefill_us=c.decode_us=c.sample_us=c.ttfa_us=7;c.pixels_mp=c.audio_input_s=3;c.load_us=123;
  auto v=_Input({2},NCHW,halide_type_of<float>());v->writeMap<float>()[0]=4;v->writeMap<float>()[1]=5;
  auto& p=*l.mGenerateParam;p.input_ids={1,2};p.input_embeds=v;p.outputs={v};p.validLogitStart=6;p.validLogitSize=2;p.max_new_tokens=12;p.timeout_ms=4;
  l.inputsEmbeds=l.attentionMask=l.positionIds=l.mPleInput=l.mTextEmbedsForPle=v;l.mCachedPromptText="cache";
  auto pos=_Input({3,1},NCHW,halide_type_of<int>());std::fill(pos->writeMap<int>(),pos->writeMap<int>()+3,8);l.mPositionIdsVarVec={pos};
  auto mask=_Input({},NCHW,halide_type_of<float>());mask->writeMap<float>()[0]=8;l.mAttentionMaskVarVec={mask};
  l.mMeta->previous=9;l.mMeta->remove=2;l.mMeta->add=4;l.mMeta->reserveHost={1,2};l.mMeta->reserve=l.mMeta->reserveHost.data();l.mMeta->n_reserve=1;l.mMeta->spec_block=2;
  l.mMeta->file_operation=KVMeta::PendingWrite;l.mMeta->file_name="synthetic";l.mMeta->file_flag=KVMeta::PendingRead;l.mMeta->seqlen_in_disk=3;l.mMeta->layer_index=1;l.mMeta->layer_nums=8;l.mMeta->attn_scale=0.5f;
 }
 static void check(Llm& l) {
  auto& c=*l.mContext;require(c.os==nullptr,"STREAM_RETAINED");
  require(c.end_with.empty()&&c.generate_str.empty()&&c.log_buffer.empty()&&c.history_tokens.empty()&&c.output_tokens.empty(),"CONTEXT_TEXT_RETAINED");
  require(c.prompt_len==0&&c.gen_seq_len==0&&c.all_seq_len==0&&c.current_token==-1,"CONTEXT_COUNTER_RETAINED");
  require(c.load_us==123&&c.vision_us==0&&c.audio_us==0&&c.prefill_us==0&&c.decode_us==0&&c.sample_us==0&&c.ttfa_us==0&&c.pixels_mp==0&&c.audio_input_s==0,"PERF_RESET_WRONG");
  auto& p=*l.mGenerateParam;require(p.outputs.empty(),"PARAM_OUTPUTS_RETAINED");require(p.input_ids.empty()&&nullptr==p.input_embeds&&p.validLogitStart==0&&p.validLogitSize==0&&p.max_new_tokens==0&&p.timeout_ms==l.mConfig->timeout_ms(),"PARAM_FIELDS_RETAINED");
  require(nullptr==l.inputsEmbeds&&nullptr==l.attentionMask&&nullptr==l.positionIds&&nullptr==l.mPleInput&&nullptr==l.mTextEmbedsForPle&&l.mCachedPromptText.empty(),"LLM_INPUT_RETAINED");
  require(l.mPositionIdsVarVec.size()==1&&l.mPositionIdsVarVec[0]->getInfo()->dim==std::vector<int>({3,1}),"POSITION_SHAPE_LOST");
  for(int i=0;i<3;++i) require(l.mPositionIdsVarVec[0]->readMap<int>()[i]==0,"POSITION_CONTENT_RETAINED");
  require(l.mAttentionMaskVarVec[0]->readMap<float>()[0]==0,"MASK_CONTENT_RETAINED");
  require(l.mMeta->previous==9,"KV_PREVIOUS_LOST");require(l.mMeta->remove==9,"KV_REMOVE_NOT_PREVIOUS");
  require(l.mMeta->add==0&&l.mMeta->reserve==nullptr&&l.mMeta->n_reserve==0&&l.mMeta->reserveHost.empty()&&l.mMeta->spec_block==0,"KV_REQUEST_FIELDS_RETAINED");
  require(l.mMeta->file_operation==KVMeta::NoChange&&l.mMeta->file_name.empty()&&l.mMeta->file_flag==KVMeta::NoChange&&l.mMeta->seqlen_in_disk==0&&l.mMeta->layer_index==0&&l.mMeta->layer_nums==8&&l.mMeta->attn_scale==0.5f,"KV_PERSISTENT_IDENTITY_WRONG");
  require(l.mConfig->asr_language().empty(),"AUTO_LANGUAGE_RETAINED");
  if(l.mTokenizer)require(l.apply_chat_template(ChatMessages{{"user","public"}}).empty(),"AUTO_TEMPLATE_CONTEXT_RETAINED");
 }
 static std::shared_ptr<Module> syntheticModule() {
  auto x=_Input({2},NCHW,halide_type_of<float>());x->setName("x");auto y=_Add(x,_Scalar<float>(1));y->setName("y");auto bytes=Variable::save({y});
  BackendConfig bn;bn.flags=4;ScheduleConfig sc;sc.type=MNN_FORWARD_CPU;sc.numThread=2;sc.backendConfig=&bn;
  std::shared_ptr<Executor::RuntimeManager> rt(Executor::RuntimeManager::createRuntimeManager(sc));
  auto m=std::shared_ptr<Module>(Module::load({"x"},{"y"},reinterpret_cast<const uint8_t*>(bytes.data()),bytes.size(),rt),Module::destroy);require(m!=nullptr,"HELPER_MODULE_LOAD_FAILED");
  auto input=_Input({2},NCHW,halide_type_of<float>());input->writeMap<float>()[0]=2;input->writeMap<float>()[1]=3;auto out=m->onForward({input});require(out.size()==1&&out[0]->readMap<float>()[0]==3,"HELPER_MODULE_FORWARD_FAILED");return m;
 }
 static void reusedModule(Module* m) {
  for(int n:{2,3,2}){auto in=_Input({n},NCHW,halide_type_of<float>());for(int i=0;i<n;++i)in->writeMap<float>()[i]=i+20*n;auto out=m->onForward({in});require(out.size()==1,"HELPER_REUSE_FORWARD");for(int i=0;i<n;++i)require(out[0]->readMap<float>()[i]==i+20*n+1,"HELPER_REUSE_NUMERIC");}
 }
 static void run(bool omni,const std::string& vocab) {
  auto conf=config();std::unique_ptr<Llm> ptr(omni?static_cast<Llm*>(new Omni(conf)):new Llm(conf));Llm& l=*ptr;
  l.mTokenizer.reset(Tokenizer::createTokenizer(vocab));require(l.mTokenizer!=nullptr,"SYNTHETIC_TOKENIZER_LOAD_FAILED");
  std::ostringstream stream;inject(l,stream);const auto context=l.mContext.get();const auto meta=l.mMeta.get();auto executor=l.mExecutor.get();
  require(l.apply_chat_template(ChatMessages{{"user","public"}})=="English","TEMPLATE_LANGUAGE_NOT_SEEDED");
  // Bind actual graph resources to the real unloaded class helper without
  // forging loaded readiness, generation strategy or full model load success.
  auto model=syntheticModule();auto extra=syntheticModule();l.mModule=model;l.mModulePool[std::make_pair(100,false)]=model;l.mModulePool[std::make_pair(1,false)]=extra;
  std::shared_ptr<Module> audio;
  if(omni){audio=syntheticModule();static_cast<Omni&>(l).mAudioModule=audio;}
  if(omni) {
   auto& o=static_cast<Omni&>(l);auto v=_Scalar<float>(5);o.mExtraArgs={v};o.mVisionEmbeddings={v};o.mAudioEmbeddings={v};o.mDeepStackEmbeddings={v};
   o.mPositionIds.push_back(3,4,5,6);o.mVisionPositionIdsCache=o.mVisionAttentionMaskCache=o.mVisionWindowAttentionMaskCache=o.mVisionIdxTensorCache=o.mVisionWeightTensorCache=o.mVisionWindowIndexCache=v;o.mVisionNum=2;o.mThinkerElapsedUs=10;
   require(o.clearOmniRequestFields(),"OMNI_HELPER_FAILED");
   require(o.mAudioModule.get()==audio.get(),"AUDIO_LOADED_RESOURCE_REPLACED");
   require(o.mPositionIds.mT.empty()&&o.mPositionIds.mH.empty()&&o.mPositionIds.mW.empty()&&o.mPositionIds.mX.empty(),"OMNI_POSITION_RETAINED");
   require(o.mAudioEmbeddings.empty(),"OMNI_AUDIO_RETAINED");
   require(o.mExtraArgs.empty()&&o.mVisionEmbeddings.empty()&&o.mDeepStackEmbeddings.empty()&&o.mVisionNum==0&&o.mThinkerElapsedUs==0,"OMNI_FIELDS_RETAINED");
   require(nullptr==o.mVisionPositionIdsCache&&nullptr==o.mVisionAttentionMaskCache&&nullptr==o.mVisionWindowAttentionMaskCache&&nullptr==o.mVisionIdxTensorCache&&nullptr==o.mVisionWeightTensorCache&&nullptr==o.mVisionWindowIndexCache,"OMNI_CACHE_RETAINED");
  }
  require(l.clearRequestStateFields(),"PRODUCTION_FIELDS_HELPER_FAILED");check(l);
  require(l.mModule.get()==model.get()&&l.mModulePool.at(std::make_pair(1,false)).get()==extra.get(),"MODULE_POOL_RESOURCE_REPLACED");

  require(l.mContext.get()==context&&l.mMeta.get()==meta&&l.mExecutor.get()==executor,"LLM_IDENTITY_CHANGED");
  reusedModule(model.get());reusedModule(extra.get());if(audio)reusedModule(audio.get());
  // Real unloaded instances must not pass the loaded readiness/public API.
  require(l.getContext()->status==LlmStatus::NOT_LOADED,"HELPER_FAKED_LOADED");
  l.mContext->os=&stream;require(!l.clearRequestState(),"UNLOADED_PUBLIC_API_ACCEPTED");require(l.mContext->os==nullptr,"REJECT_STREAM_RETAINED");
  l.mContext->status=LlmStatus::NORMAL_FINISHED;l.mContext->os=&stream;
  require(!l.clearRequestState(),"SYNTHETIC_STATUS_FAKED_LOADED_SUCCESS");require(l.mContext->os==nullptr,"REJECT_STREAM_RETAINED");
  l.set_config(R"({"async":true})");require(!l.clearRequestState(),"UNSUPPORTED_PROFILE_ACCEPTED");
 }
};}}
int main(int argc,char**argv){try{require(argc==3,"ARGS");bool omni=std::string(argv[1])=="omni";MNN::Transformer::RequestCleanupProbe::run(omni,argv[2]);std::cout<<"PASS real "<<(omni?"Omni":"Llm")<<" unloaded production-helper fields/identity/auto; public unloaded reject"<<std::endl;return 0;}catch(const std::exception&e){std::cerr<<"ASSERTION: "<<e.what()<<std::endl;return 2;}}
