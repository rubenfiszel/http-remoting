/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.remoting3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import okhttp3.Call;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Test;

public class InterruptibleCallTest {
    private final Call call = mock(Call.class);
    private final InterruptibleCall interruptibleCall = new InterruptibleCall(call);

    @Test(timeout = 1_000)
    public void when_execute_is_called_and_the_thread_interrupted_the_underlying_call_should_be_cancelled()
            throws IOException, InterruptedException {

        CountDownLatch underlyingExecuteCalled = new CountDownLatch(1);

        when(call.execute()).thenAnswer(invocation -> {
            underlyingExecuteCalled.countDown();
            Thread.sleep(999999999L);
            return null;
        });

        Thread thread = new Thread(() -> {
            try {
                interruptibleCall.execute();
            } catch (IOException e) {
                throw new RuntimeException();
            }
        });

        thread.start();

        underlyingExecuteCalled.await();
        thread.interrupt();

        thread.join();
        verify(call).cancel();
    }

    @Test(timeout = 1_000)
    public void when_execute_is_called_and_the_call_succeeds_the_response_should_be_returned() throws IOException {
        Response mockResponse = someResponse();

        when(call.execute()).thenReturn(mockResponse);

        Response response = interruptibleCall.execute();

        assertThat(response).isEqualTo(mockResponse);
    }

    @Test(timeout = 1_000)
    public void when_execute_is_called_and_the_call_fails_the_exception_should_be_thrown() throws IOException {
        IOException exception = new IOException("something bad happened");

        when(call.execute()).thenThrow(exception);

        try {
            interruptibleCall.execute();
            fail("Exception should have been thrown");
        } catch (IOException e) {
            assertThat(e).isEqualTo(exception);
        }
    }

    private Response someResponse() {
        return new Response.Builder()
                .request(new Request.Builder()
                        .url("http://lol")
                        .build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("message")
                .build();
    }

}
