package netscape.javascript;

/**
 * JavaFXのWebView(javafx.web)が、JavaScriptとの連携(WebEngine.executeScript・JSObject)で内部的に必要とする
 * クラス。本来はJDKの「jdk.jsobject」モジュールが提供するが、JDK 26ではこのモジュールが無く、無い状態で
 * executeScriptを呼ぶとNoClassDefFoundErrorの直後にネイティブクラッシュする。そのため、公開されている
 * 同名・同シグネチャのAPIの互換クラスをクラスパス上に用意している（実装はJavaFX側の
 * com.sun.webkit.dom.JSObjectが担う）。JDKにjdk.jsobjectがある環境ではそちらが先に使われる。
 */
public abstract class JSObject {

    protected JSObject() {
    }

    public abstract Object call(String methodName, Object... args) throws JSException;

    public abstract Object eval(String s) throws JSException;

    public abstract Object getMember(String name) throws JSException;

    public abstract void setMember(String name, Object value) throws JSException;

    public abstract void removeMember(String name) throws JSException;

    public abstract Object getSlot(int index) throws JSException;

    public abstract void setSlot(int index, Object value) throws JSException;
}
