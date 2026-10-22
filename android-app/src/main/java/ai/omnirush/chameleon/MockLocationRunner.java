package ai.omnirush.chameleon;

public class MockLocationRunner implements Runnable {
    private final MockLocationService service;

    public MockLocationRunner(MockLocationService service) {
        this.service = service;
    }

    @Override
    public void run() {
        service.runMockLoop();
    }
}

