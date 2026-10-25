package vn.edu.ueh.phuclu.broomsharing.repository;



public interface ResultCallback<T> {
    void onSuccess(T result);

    void onError(Exception error);
}
