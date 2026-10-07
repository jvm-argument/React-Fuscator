package fixture;

public final class ProtectionService {
    private int calls;
    private static String text(){return "fabric: Привет 🚀";}
    private int calculate(int count){int result=0;for(int i=0;i<count;i++){if((i&1)==0)result+=i*7;else result^=i*13;}return result;}
    public int run(){calls++;if(text()!=text())throw new AssertionError("String identity");return calculate(10);}
}
