package ru.yandex.practicum.mymarket.exception;

public class UsernameTakenException extends RuntimeException {

    public UsernameTakenException(String username) {
        super("Логин " + username + " уже занят");
    }
}
