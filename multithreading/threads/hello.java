package threads;
public class hello {

    public static void main(String[] args) { 
        Runnable r = () -> {System.out.println(Thread.currentThread().getId() + " Runnable -> Hello World");};


        Thread t = new Thread(r);
        t.start();

        System.out.println(Thread.currentThread().getId() + " Main -> hello world");
    }
}