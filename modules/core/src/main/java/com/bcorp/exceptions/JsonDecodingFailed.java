package com.bcorp.exceptions;

import java.io.IOException;

public class JsonDecodingFailed extends RuntimeException {
    public JsonDecodingFailed(IOException e) {
        super(e);
    }
}
