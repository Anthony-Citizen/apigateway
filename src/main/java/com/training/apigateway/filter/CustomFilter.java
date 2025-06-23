package com.training.apigateway.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.*;
import java.nio.charset.StandardCharsets;


@Component
public class CustomFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Bungkus request & response
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        BufferedResponseWrapper wrappedResponse = new BufferedResponseWrapper(response);

        // Log request
        System.out.println("[Request] " + wrappedRequest.getMethod() + " " + wrappedRequest.getRequestURI());
        wrappedRequest.getHeaderNames().asIterator().forEachRemaining(header ->
                System.out.println(header + ": " + wrappedRequest.getHeader(header)));

        String requestBody = new String(wrappedRequest.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        System.out.println("[Request Body] " + requestBody);

        // Lanjutkan ke chain
        filterChain.doFilter(wrappedRequest, wrappedResponse);

        // Log response
        String responseContent = new String(wrappedResponse.getCapturedContent(), response.getCharacterEncoding());
        System.out.println("[Response] " + response.getStatus() + " Body: " + responseContent);

        // Kirim ulang ke client
        response.getOutputStream().write(wrappedResponse.getCapturedContent());
    }

    // Custom wrapper class
    private static class BufferedResponseWrapper extends HttpServletResponseWrapper {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final ServletOutputStream outputStream = new ServletOutputStream() {
            @Override
            public boolean isReady() { return true; }
            @Override
            public void setWriteListener(WriteListener writeListener) {}
            @Override
            public void write(int b) {
                buffer.write(b);
            }
        };
        private final PrintWriter writer = new PrintWriter(buffer);

        public BufferedResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() {
            return outputStream;
        }

        @Override
        public PrintWriter getWriter() {
            return writer;
        }

        public byte[] getCapturedContent() {
            writer.flush();
            return buffer.toByteArray();
        }
    }

    public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

        private final byte[] cachedBody;

        public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            InputStream requestInputStream = request.getInputStream();
            this.cachedBody = requestInputStream.readAllBytes();
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.cachedBody);

            return new ServletInputStream() {
                @Override
                public int read() {
                    return byteArrayInputStream.read();
                }

                @Override
                public boolean isFinished() {
                    return byteArrayInputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {}
            };
        }
    }
}
