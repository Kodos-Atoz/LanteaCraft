package lc.api.defs;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Hint provider class marker
 *
 * @author AfterLifeLochie
 *
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface HintProvider {
	/**
	 * The server class name. If no instance is required on the server, do not
	 * set this field.
	 */
	String serverClass();

	/**
	 * The client class name. If no instance is required on the client, do not
	 * set this field.
	 */
	String clientClass();
}
