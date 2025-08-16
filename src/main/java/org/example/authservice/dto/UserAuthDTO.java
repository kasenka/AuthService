package org.example.authservice.dto;

import jakarta.persistence.Column;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class UserAuthDTO {
    @NotBlank(message = "Логин не может быть пустым")
    @Column(unique = true)
    private String username;

    @NotBlank(message = "Пароль не может быть пустым")
    private String password;
}
