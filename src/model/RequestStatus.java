package model;

public enum RequestStatus {
    WAITING,      // В очереди
    IN_SERVICE,   // Обслуживается
    SERVED,       // Успешно обслужена
    ABANDONED     // Ушла из-за превышения времени ожидания
}