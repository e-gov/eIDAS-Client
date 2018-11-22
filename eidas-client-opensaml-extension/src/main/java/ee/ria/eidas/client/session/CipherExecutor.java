package ee.ria.eidas.client.session;

public interface CipherExecutor<T, R> {
    R encode(T var1);

    R decode(T var1);
}