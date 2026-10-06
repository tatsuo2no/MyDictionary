package netscape.javascript;

/** JavaScriptの実行エラーを表す例外（{@link JSObject}と同じ理由で用意している互換クラス）。 */
public class JSException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public JSException() {
        super();
    }

    public JSException(String message) {
        super(message);
    }
}
