package ru.thedevs.testproject;

import io.jmix.core.DataManager;
import org.springframework.stereotype.Service;
import ru.thedevs.entities.Email;
import ru.thedevs.entities.Phone;
import ru.thedevs.entities.UserEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ContactService {

    private final DataManager dataManager;

    private final Map<UUID, String> oldEmails = new HashMap<>();
    private final Map<UUID, Long> oldPhones = new HashMap<>();

    public ContactService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    // ------------------- EMAIL -------------------

    public void requestEmailChange(UserEntity user, String newEmail) {
        UserEntity managedUser = dataManager.load(UserEntity.class)
                .id(user.getId())
                .one();

        Email current = managedUser.getEmail();
        if (current != null) {
            oldEmails.put(managedUser.getId(), current.getEmail());
        }

        Email newEmailEntity = dataManager.create(Email.class);
        newEmailEntity.setEmail(newEmail);
        newEmailEntity.setConfirmed(false);

        managedUser.setEmail(newEmailEntity);
        dataManager.save(managedUser);
    }

    public void confirmEmail(UserEntity user, String code) {
        if (isValidEmailCode(code)) {
            UserEntity managedUser = dataManager.load(UserEntity.class)
                    .id(user.getId())
                    .one();

            if (managedUser.getEmail() != null) {
                managedUser.getEmail().setConfirmed(true);
                dataManager.save(managedUser);
                oldEmails.remove(user.getId());
            }
        } else {
            throw new RuntimeException("WrongCode");
        }
    }

    public void rollbackEmailChange(UserEntity user) {
        UserEntity managedUser = dataManager.load(UserEntity.class)
                .id(user.getId())
                .one();

        Email current = managedUser.getEmail();
        if (current != null && !Boolean.TRUE.equals(current.getConfirmed())) {
            String oldEmail = oldEmails.get(managedUser.getId());
            if (oldEmail != null) {
                Email restored = dataManager.create(Email.class);
                restored.setEmail(oldEmail);
                restored.setConfirmed(true);
                managedUser.setEmail(restored);
            } else {
                managedUser.setEmail(null);
            }
            dataManager.save(managedUser);
            oldEmails.remove(managedUser.getId());
        }
    }

    private boolean isValidEmailCode(String code) {
        return "1234".equals(code);
    }

    // ------------------- PHONE -------------------

    public void requestPhoneChange(UserEntity user, Long newPhone) {
        UserEntity managedUser = dataManager.load(UserEntity.class)
                .id(user.getId())
                .one();

        Phone current = managedUser.getPhone();
        if (current != null) {
            oldPhones.put(managedUser.getId(), current.getNumber());
        }

        Phone newPhoneEntity = dataManager.create(Phone.class);
        newPhoneEntity.setNumber(newPhone);
        newPhoneEntity.setConfirmed(false);

        managedUser.setPhone(newPhoneEntity);
        dataManager.save(managedUser);
    }

    public void confirmPhone(UserEntity user, String code) {
        if (isValidPhoneCode(code)) {
            UserEntity managedUser = dataManager.load(UserEntity.class)
                    .id(user.getId())
                    .one();

            if (managedUser.getPhone() != null) {
                managedUser.getPhone().setConfirmed(true);
                dataManager.save(managedUser);
                oldPhones.remove(user.getId());
            }
        } else {
            throw new RuntimeException("WrongCode");
        }
    }

    public void rollbackPhoneChange(UserEntity user) {
        UserEntity managedUser = dataManager.load(UserEntity.class)
                .id(user.getId())
                .one();

        Phone current = managedUser.getPhone();
        if (current != null && !Boolean.TRUE.equals(current.getConfirmed())) {
            Long oldPhone = oldPhones.get(managedUser.getId());
            if (oldPhone != null) {
                Phone restored = dataManager.create(Phone.class);
                restored.setNumber(oldPhone);
                restored.setConfirmed(true);
                managedUser.setPhone(restored);
            } else {
                managedUser.setPhone(null);
            }
            dataManager.save(managedUser);
            oldPhones.remove(managedUser.getId());
        }
    }

    private boolean isValidPhoneCode(String code) {
        return "5678".equals(code);
    }
}

