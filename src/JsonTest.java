import Util.JsonUtil;

public class JsonTest {

    public static void main(String[] args) {

        String json =
                "{"
                        + "\"transactions\":["
                        + "{\"id\":\"1\"},"
                        + "{\"id\":\"2\"}"
                        + "]"
                        + "}";

        String array = JsonUtil.extractArray(json, "transactions");

        System.out.println(array);

        System.out.println(JsonUtil.splitJsonArray(array));
    }
}