package ru.library.event.service;

import org.springframework.stereotype.Service;
import ru.library.event.model.Manager;
import ru.library.event.repository.ManagerRepository;

import java.util.List;
import java.util.Optional;

@Service
public class ManagerService {
    private final ManagerRepository managerRepository;

    public ManagerService(ManagerRepository managerRepository) {
        this.managerRepository = managerRepository;
    }

    public Manager createManager(Manager manager) {
        return managerRepository.save(manager);
    }

    public Optional<Manager> getManager(String id) {
        return managerRepository.findById(id);
    }

    public List<Manager> getAllManagers() {
        return managerRepository.findAll();
    }

    public Manager updateManager(Manager manager) {
        return managerRepository.save(manager);
    }

    public void deleteManager(String id) {
        managerRepository.deleteById(id);
    }
}
