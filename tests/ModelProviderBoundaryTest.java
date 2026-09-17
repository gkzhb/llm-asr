import org.llmasr.minimal.modelmanagement.ModelManagementController;
import org.llmasr.minimal.modelmanagement.ModelManagementState;
import org.llmasr.minimal.model.ModelProviderBoundary;
import org.llmasr.minimal.model.ModelRepository;
import org.llmasr.minimal.model.ModelSource;
import org.llmasr.minimal.modelmanagement.ModelUiText;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Executes the production provider-call and stream boundaries, not ContentResolver stubs. */
public final class ModelProviderBoundaryTest {
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    static void providerFailure(int kind)throws IOException {
        String secret="Private account alice@example.test Confidential";
        switch(kind) {
            case 0:throw new CancellationException(secret);
            case 1:ModelRepository.CancelledException e=new ModelRepository.CancelledException();e.initCause(new IOException(secret));e.addSuppressed(new IOException(secret));throw e;
            case 2:throw new SecurityException(secret);
            case 3:throw new FileNotFoundException(secret);
            case 4:throw new LinkageError(secret);
            default:throw new IllegalStateException(secret); // Android OperationCanceledException is also RuntimeException.
        }
    }
    static void assertSafe(Throwable e) {
        check(e instanceof IOException&&!(e instanceof ModelRepository.CancelledException),"provider-authored cancellation is safe failure, not own gate");
        check(e.getMessage().equals(ModelProviderBoundary.FILE_FAILURE)||e.getMessage().equals(ModelProviderBoundary.DIRECTORY_FAILURE),"fixed safe message");
        check(e.getCause()==null&&e.getSuppressed().length==0,"provider cause/suppressed cannot escape");
    }
    public static void main(String[] args)throws Exception {
        for(int k=0;k<6;k++) {
            final int kind=k;
            // Shared supplier wrapper used at each query/cursor/URI callsite in SAF.
            // This executes that wrapper, not seven pretend Android cursor methods.
            try {ModelProviderBoundary.call(ModelProviderBoundary.DIRECTORY_FAILURE,()->{providerFailure(kind);return null;});throw new AssertionError("directory supplier did not fail");}
            catch(IOException e){assertSafe(e);}
            for(int stage=0;stage<4;stage++) {
                final int fault=stage;
                try {
                    InputStream in=ModelProviderBoundary.open(()->{
                        if(fault==0)providerFailure(kind);
                        return new InputStream(){
                            public int read()throws IOException{providerFailure(kind);return -1;}
                            public int read(byte[] b,int off,int len)throws IOException{providerFailure(kind);return -1;}
                            public void close()throws IOException{if(fault==3)providerFailure(kind);}
                        };
                    });
                    if(fault==1)in.read();if(fault==2)in.read(new byte[3],0,3);if(fault==3)in.close();
                    throw new AssertionError("boundary did not fail "+fault);
                }catch(IOException e){assertSafe(e);}
            }
            ModelManagementControllerTest.Fixture f=new ModelManagementControllerTest.Fixture();
            ModelManagementController c=f.controller();
            ModelSource source=new ModelSource(){
                public Map<String,UriRef> enumerate(Collection<String> n,int max){Map<String,UriRef> m=new HashMap<>();for(String name:n)m.put(name,new UriRef(name));return m;}
                public InputStream open(UriRef ref)throws IOException{return ModelProviderBoundary.open(()->{providerFailure(kind);return null;});}
            };
            c.startImport(source);f.queue.run();
            check(f.state.current().phase==ModelManagementState.Phase.FAILED,"unsolicited provider cancellation FAILED");
            check(f.state.current().errorCode.equals(ModelProviderBoundary.FILE_FAILURE),"snapshot sanitized");
            check(!ModelUiText.task(f.state.current()).contains("Confidential")&&f.reports.log.equals(java.util.Arrays.asList("pending:INFERENCE","terminal:INFERENCE")),"UI/report isolation");
        }
        try { ModelProviderBoundary.checkpoint(new ModelRepository.CancelGate(){public boolean cancelled(){return true;}public boolean reservePublish(){return false;}});throw new AssertionError("gate"); }
        catch(ModelRepository.CancelledException own){check(own.getMessage().equals("已取消")&&own.getCause()==null,"genuine typed gate cancellation");}
        // A real sanitized stream may block in close: keep owner, then report gate cancel independently.
        ModelManagementControllerTest.Fixture f=new ModelManagementControllerTest.Fixture();ModelManagementController c=f.controller();
        ModelRepositoryCancelTest.Gate close=new ModelRepositoryCancelTest.Gate();
        ModelSource src=new ModelSource(){
            public Map<String,UriRef> enumerate(Collection<String> n,int max){Map<String,UriRef> m=new HashMap<>();for(String name:n)m.put(name,new UriRef(name));return m;}
            public InputStream open(UriRef r)throws IOException{return ModelProviderBoundary.open(()->new ByteArrayInputStream(ModelManagementControllerTest.DATA){
                public void close()throws IOException{close.block();providerFailure(0);}
            });}
        };
        c.startImport(src);
        try(ModelRepositoryCancelTest.Worker w=new ModelRepositoryCancelTest.Worker(()->f.queue.run())) {
            try{close.await();c.requestCancel(f.control.peekActive().id);check(f.owner.isBusy()&&!c.startVerify(),"owner retained through boundary close");}
            finally{close.close();}
            w.join();check(w.error.get()==null,"worker failure observed");
        }
        check(f.state.current().phase==ModelManagementState.Phase.CANCELLED&&!f.owner.isBusy(),"genuine gate wins after sanitized close failure");
        check(f.state.current().errorCode.equals(ModelProviderBoundary.FILE_FAILURE),"cancel does not expose provider message");
        System.out.println("PASS production provider boundary exception/stream/owner regressions");
    }
}
