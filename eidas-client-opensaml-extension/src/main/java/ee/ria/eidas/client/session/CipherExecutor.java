package ee.ria.eidas.client.session;

/* FIXME: Do we need generics?
 *        If `encode` consumes type `T` and returns type `R`, shouldn't `decode` consume type `R` and return type `T`?
 */
public interface CipherExecutor<T, R> {
    R encode(T var1);

    R decode(T var1);
}
