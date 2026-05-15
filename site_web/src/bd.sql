CREATE DATABASE restaurante_db;
USE restaurante_db;

CREATE TABLE mesa (
    id INT PRIMARY KEY AUTO_INCREMENT,
    numero_mesa INT NOT NULL,
    reservada BOOLEAN NOT NULL
);

CREATE TABLE usuario (
    id INT PRIMARY KEY AUTO_INCREMENT,
    nombre VARCHAR(100) NOT NULL,
    correo VARCHAR(100) NOT NULL,
    contrasena VARCHAR(100) NOT NULL,
    rol VARCHAR(50) NOT NULL
);