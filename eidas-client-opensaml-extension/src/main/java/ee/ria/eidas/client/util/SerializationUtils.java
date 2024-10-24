package ee.ria.eidas.client.util;

import com.google.common.base.Throwables;
import ee.ria.eidas.client.session.CipherExecutor;
import lombok.extern.slf4j.Slf4j;

import java.io.*;

@Slf4j
public final class SerializationUtils {
    
    private SerializationUtils() {
    }

    public static byte[] serialize(final Serializable object) {
        final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        serialize(object, outBytes);
        return outBytes.toByteArray();
    }

    public static void serialize(final Serializable object, final OutputStream outputStream) {
        try (ObjectOutputStream out = new ObjectOutputStream(outputStream)) {
            out.writeObject(object);
        } catch (final IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public static <T> T deserialize(final byte[] inBytes) {
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(inBytes);
        return deserialize(inputStream);
    }

    public static <T> T deserialize(final InputStream inputStream) {
        try (ObjectInputStream in = new ObjectInputStream(inputStream)) {
            return (T) in.readObject();
        } catch (final ClassNotFoundException | IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public static byte[] serializeAndEncodeObject(final CipherExecutor cipher,
                                                  final Serializable object) {
        final byte[] outBytes = serialize(object);
        return (byte[]) cipher.encode(outBytes);
    }

    public static <T> T decodeAndDeserializeObject(final byte[] object,
                                                   final CipherExecutor cipher,
                                                   final Class<? extends Serializable> type) {
        try {
            final byte[] decoded = (byte[]) cipher.decode(object);
            return deserializeAndCheckObject(decoded, type);
        } catch (final Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public static <T> T deserializeAndCheckObject(final byte[] object, final Class<? extends Serializable> type) {
        final Object result = deserialize(object);
        if (!type.isAssignableFrom(result.getClass())) {
            throw new ClassCastException("Decoded object is of type " + result.getClass()
                    + " when we were expecting " + type);
        }
        return (T) result;
    }
}
