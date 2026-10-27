// Public generated graphs only. Normal Module onForward/resize owns rebinding.
#include <MNN/expr/ExprCreator.hpp>
#include <MNN/expr/Module.hpp>
#include <MNN/expr/ExecutorScope.hpp>
#include <MNN/Interpreter.hpp>
#include "module/StaticModule.hpp"
#include "core/TensorUtils.hpp"
#include "backend/cpu/CPUAttention.hpp"
#include "MNN_generated.h"
#include "native_request_probe_support.hpp"
#include <algorithm>
#include <cmath>
#include <set>
namespace MNN { namespace Express {
struct RequestCleanupProbe {
 struct Identity {const void* session;const void* resource;const void* shared;std::vector<const void*> constants;};
 static Identity identity(StaticModule*m){Identity r{m->mSession.get(),m->mResource.get(),m->mResource->mSharedConst.get(),{}};for(auto&t:m->mResource->mSharedConst->allTensors)if(t)r.constants.push_back(t.get());return r;}
 static bool rebound(StaticModule*m,const std::set<const void*>&hosts){
  require(m->getSession()->getPipelineInfo(0).first.cache.first->type()==MNN_FORWARD_CPU,"BACKEND_NOT_CPU");
  bool borrowed=false;for(auto*t:m->mInputTensors)if(t&&hosts.count(t->host<void>()))borrowed=true;
  return borrowed;
 }
};}}
namespace MNN { struct RequestCleanupProbe {
 static const void* manager(CPUAttention*e){return e->mKVCacheManager.get();}
 static int length(CPUAttention*e){return e->mKVCacheManager->kvLength();}
};}
namespace MNN { namespace Transformer {struct RequestCleanupProbe {
 static void attach(Llm&l,std::shared_ptr<KVMeta>m){l.mMeta=m;}
 static bool clean(Llm&l){return l.clearRequestStateFields();}
};}}
static void collect(Module*m,std::vector<StaticModule*>&r){if(m->type()=="StaticModule")r.push_back(static_cast<StaticModule*>(m));for(auto&c:m->getChildren())collect(c.get(),r);}
static ScheduleConfig schedule(BackendConfig&b){b.flags=4;b.precision=BackendConfig::Precision_High;ScheduleConfig s;s.type=MNN_FORWARD_CPU;s.numThread=2;s.backendConfig=&b;return s;}
static std::shared_ptr<Module> load(const std::vector<int8_t>&b,const std::vector<std::string>&names,KVMeta*meta=nullptr){
 BackendConfig bc;auto sc=schedule(bc);std::shared_ptr<Executor::RuntimeManager>rt(Executor::RuntimeManager::createRuntimeManager(sc));if(meta){rt->setHintPtr(Interpreter::KVCACHE_INFO,meta);rt->setHint(Interpreter::ATTENTION_OPTION,8);}
 Module::Config mc;mc.shapeMutable=true;mc.rearrange=meta!=nullptr;
 auto m=std::shared_ptr<Module>(Module::load(names,{"y"},reinterpret_cast<const uint8_t*>(b.data()),b.size(),rt,&mc),Module::destroy);require(bool(m),"MODULE_LOAD");return m;
}
static std::vector<float> values(const std::vector<VARP>&out){require(out.size()==1&&nullptr!=out[0]&&out[0]->getInfo(),"OUTPUT_INFO");auto p=out[0]->readMap<float>();require(p,"OUTPUT_DATA");return {p,p+out[0]->getInfo()->size};}
static void equal(const std::vector<float>&a,const std::vector<float>&b,const char*msg){require(a.size()==b.size(),msg);for(size_t i=0;i<a.size();++i)require(std::abs(a[i]-b[i])<1e-4f,msg);}
static std::vector<int8_t> graph(const std::string&kind){
 auto x=_Input({2,3},NCHW,halide_type_of<float>());x->setName("x");VARP y;
 if(kind=="reshape"){auto shape=_Input({2},NCHW,halide_type_of<int>());shape->setName("shape");y=_Add(_Reshape(x,shape),_Scalar<float>(1.25f));}
 else if(kind=="matmul"){float weights[]={1,2,3,4,5,6};y=_MatMul(x,_Const(weights,{3,2},NCHW));}
 else y=_Add(x,_Scalar<float>(1.25f));y->setName("y");return Variable::save({y});
}
static void sequence(std::shared_ptr<Module>m,const std::vector<int8_t>&bytes,const std::string&kind,bool stale){
 std::vector<StaticModule*>statics;collect(m.get(),statics);require(!statics.empty(),"STATIC_MISSING");std::vector<MNN::Express::RequestCleanupProbe::Identity>ids;for(auto*s:statics)ids.push_back(MNN::Express::RequestCleanupProbe::identity(s));
 std::vector<VARP>previous;std::vector<std::vector<float>>churn;
 // Same shape/new content, same shape/new control, different shape, A again.
 for(int round=0;round<5;++round){int rows=round==3?4:2,cols=3;int seed=round==0||round==4?1:round+3;
  auto x=_Input({rows,cols},NCHW,halide_type_of<float>());auto p=x->writeMap<float>();for(int i=0;i<rows*cols;++i)p[i]=0.5f*(i+1)+seed*10;
  std::vector<VARP>in{x};std::vector<std::string>names{"x"};if(kind=="reshape"){auto c=_Input({2},NCHW,halide_type_of<int>());c->writeMap<int>()[0]=round==2?rows:cols;c->writeMap<int>()[1]=round==2?cols:rows;in.push_back(c);names.push_back("shape");}
  // Sensitivity negative deliberately passes old INPUT content, not a fake
  // backend bug. It must fail numeric fresh-equality on the second execution.
  if(!previous.empty()){require(previous[0]->readMap<float>()!=in[0]->readMap<float>(),"INPUT_ADDRESS_NOT_DISTINCT");if(!(stale&&round==1))previous.clear();}
  auto out=m->onForward(stale&&round==1?previous:in);auto got=values(out);auto fresh=load(bytes,names);auto expected=values(fresh->onForward(in));equal(got,expected,"NUMERIC_REBIND_DIFFERS_FRESH");
  if(kind=="reshape")require(out[0]->getInfo()->dim==std::vector<int>({round==2?rows:cols,round==2?cols:rows}),"CONTROL_CONTENT_NOT_REBOUND");
  if(kind=="matmul"){int actual=0;for(auto*s:statics)for(auto&info:s->getSession()->getPipelineInfo(0).second)for(auto&e:info.executionCache)if(e.first->type()==OpType_MatMul&&e.second->backend()->type()==MNN_FORWARD_CPU)++actual;require(actual>0,"ACTUAL_CPU_MATMUL_NOT_REACHED");}
  std::set<const void*>hosts;for(auto&v:in)hosts.insert(v->readMap<uint8_t>());bool rebound=false;for(size_t i=0;i<statics.size();++i){auto now=MNN::Express::RequestCleanupProbe::identity(statics[i]);require(now.session==ids[i].session&&now.resource==ids[i].resource&&now.shared==ids[i].shared&&now.constants==ids[i].constants,"SESSION_RESOURCE_CHANGED");rebound=MNN::Express::RequestCleanupProbe::rebound(statics[i],hosts)||rebound;}require(rebound,"INPUT_CURRENT_ADDRESS_NOT_REBOUND");
  // Inputs and outputs remain live until all reads completed; next inputs have
  // distinct live allocations. Then old external owners naturally release;
  // allocator churn tests next rebind, not invalid idle-null assumptions.
  previous=in;out.clear();fresh.reset();in.clear();x=nullptr;
  churn.assign(32,std::vector<float>(rows*cols,1234.0f));
  std::cout<<"PASS "<<kind<<" round="<<round<<" normal reuse/fresh numeric"<<std::endl;
 }
}
static void moduleCase(const std::string&kind,bool stale){auto b=graph(kind);auto names=kind=="reshape"?std::vector<std::string>{"x","shape"}:std::vector<std::string>{"x"};auto m=load(b,names);sequence(m,b,kind,stale);std::shared_ptr<Module>clone(Module::clone(m.get(),true),Module::destroy);require(bool(clone),"CLONE_LOAD");std::vector<StaticModule*>a,c;collect(m.get(),a);collect(clone.get(),c);require(a.size()==c.size(),"CLONE_PARTS");for(auto*s:a){auto x=MNN::Express::RequestCleanupProbe::identity(s);auto it=std::find_if(c.begin(),c.end(),[&](StaticModule*v){return MNN::Express::RequestCleanupProbe::identity(v).resource==x.resource;});require(it!=c.end(),"CLONE_RESOURCE_NOT_SHARED");auto y=MNN::Express::RequestCleanupProbe::identity(*it);require(x.session!=y.session&&x.shared==y.shared&&x.constants==y.constants,"CLONE_SESSION_CONST_CONTRACT");}sequence(clone,b,kind,false);sequence(m,b,kind,false);}
static void sessionCase(){auto b=graph("add");std::unique_ptr<Interpreter>net(Interpreter::createFromBuffer(b.data(),b.size()));require(bool(net),"INTERPRETER_LOAD");BackendConfig bc;auto sc=schedule(bc);auto*s=net->createSession(sc);require(s,"SESSION_LOAD");for(int n:{2,4,2}){auto input=net->getSessionInput(s,"x");net->resizeTensor(input,{n,3});net->resizeSession(s);auto p=static_cast<float*>(input->map(Tensor::MAP_TENSOR_WRITE,Tensor::CAFFE));require(p,"MAP_INPUT");for(int i=0;i<n*3;++i)p[i]=i+n;input->unmap(Tensor::MAP_TENSOR_WRITE,Tensor::CAFFE,p);require(net->runSession(s)==NO_ERROR,"RUN_SESSION");auto output=net->getSessionOutput(s,"y");auto q=static_cast<const float*>(output->map(Tensor::MAP_TENSOR_READ,Tensor::CAFFE));require(q,"MAP_OUTPUT");for(int i=0;i<n*3;++i)require(std::abs(q[i]-(i+n+1.25f))<1e-5f,"SESSION_NUMERIC");output->unmap(Tensor::MAP_TENSOR_READ,Tensor::CAFFE,const_cast<float*>(q));}std::cout<<"PASS same Interpreter Session map/unmap A-B-A"<<std::endl;}
static std::vector<int8_t> attentionGraph(){auto q=_Input({1,2,1,8},NCHW,halide_type_of<float>()),k=_Input({1,2,1,8},NCHW,halide_type_of<float>()),v=_Input({1,2,1,8},NCHW,halide_type_of<float>()),mask=_Input({},NCHW,halide_type_of<float>());q->setName("q");k->setName("k");v->setName("v");mask->setName("mask");OpT op;op.type=OpType_Attention;op.main.type=OpParameter_AttentionParam;op.main.value=new AttentionParamT;op.main.AsAttentionParam()->kv_cache=true;auto y=Variable::create(Expr::create(&op,{q,k,v,mask}));y->setName("y");return Variable::save({y});}
static std::vector<float> attentionRun(Module*m,KVMeta&meta,int n,int seed){meta.add=n;std::vector<VARP>in;for(int t=0;t<3;++t){auto x=_Input({1,n,1,8},NCHW,halide_type_of<float>());for(int i=0;i<n*8;++i)x->writeMap<float>()[i]=0.01f*(i+1)*(t+1)+seed*0.125f;in.push_back(x);}auto mask=_Input({},NCHW,halide_type_of<float>());mask->writeMap<float>()[0]=0;in.push_back(mask);auto r=values(m->onForward(in));meta.sync();return r;}
static void attentionCase(){auto conf=config();Llm l(conf);auto meta=std::make_shared<KVMeta>();MNN::Transformer::RequestCleanupProbe::attach(l,meta);auto b=attentionGraph();auto m=load(b,{"q","k","v","mask"},meta.get());std::vector<StaticModule*>statics;collect(m.get(),statics);CPUAttention*execution=nullptr;for(auto*s:statics)for(auto&info:s->getSession()->getPipelineInfo(0).second)for(auto&e:info.executionCache)if(e.first->type()==OpType_Attention)execution=static_cast<CPUAttention*>(e.second.get());require(execution,"ATTENTION_EXECUTION_MISSING");auto manager=MNN::RequestCleanupProbe::manager(execution);
 for(int round=0;round<3;++round){int n=round==1?3:2;auto got=attentionRun(m.get(),*meta,n,round+1);KVMeta fm;auto fresh=load(b,{"q","k","v","mask"},&fm);equal(got,attentionRun(fresh.get(),fm,n,round+1),"KV_RESULT_DIFFERS_FRESH");require(MNN::RequestCleanupProbe::length(execution)==n&&MNN::RequestCleanupProbe::manager(execution)==manager,"KV_MANAGER_IDENTITY_LENGTH");require(MNN::Transformer::RequestCleanupProbe::clean(l),"META_CLEAN_FAILED");require(meta->previous==size_t(n)&&meta->remove==size_t(n),"META_REMOVE_PREVIOUS");std::shared_ptr<Module>clone(Module::clone(m.get(),true),Module::destroy);auto cg=attentionRun(clone.get(),*meta,n+1,9);KVMeta cm;auto cf=load(b,{"q","k","v","mask"},&cm);equal(cg,attentionRun(cf.get(),cm,n+1,9),"CLONE_KV_RESULT_DIFFERS_FRESH");require(MNN::RequestCleanupProbe::length(execution)==n+1,"CLONE_MANAGER_NOT_SHARED");require(MNN::Transformer::RequestCleanupProbe::clean(l),"CLONE_META_CLEAN");}
 std::cout<<"PASS actual CPUAttention next-execute KV replacement, shared manager clone, stable resource, fresh numeric"<<std::endl;
}
int main(int argc,char**argv){try{require(argc>=2,"ARGS");BackendConfig bc;bc.flags=4;bc.precision=BackendConfig::Precision_High;auto executor=Executor::newExecutor(MNN_FORWARD_CPU,bc,2);ExecutorScope scope(executor);std::string kind=argv[1];if(kind=="attention")attentionCase();else if(kind=="session")sessionCase();else moduleCase(kind,argc>2);return 0;}catch(const std::exception&e){std::cerr<<"ASSERTION: "<<e.what()<<std::endl;return 2;}}
