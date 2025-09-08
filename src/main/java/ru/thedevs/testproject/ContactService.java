package ru.thedevs.testproject;

import io.jmix.core.DataManager;
import io.jmix.core.Messages;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.thedevs.entities.Email;
import ru.thedevs.entities.Phone;

@Service
public class ContactService {

    @Autowired
    private Messages messages;
    @Autowired
    private DataManager dataManager;

    public Email createNewEmail(String newEmail) {
        Email email = dataManager.create(Email.class);
        email.setEmail(newEmail);
        email.setConfirmed(false);
        return email;
    }

    public boolean isValidEmailCode(String code) {
        return "1234".equals(code);
    }

    public Phone createNewPhone(Long newPhone) {
        Phone phone = dataManager.create(Phone.class);
        phone.setNumber(newPhone);
        phone.setConfirmed(false);
        return phone;
    }

    public boolean isValidPhoneCode(String code) {
        return "5678".equals(code);
    }
}



