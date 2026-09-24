package ru.library.event.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.library.event.model.Manager;
import ru.library.event.repository.KeyValueRepository;

import java.util.List;
import java.util.Optional;

@Service
public class ManagerService {

    private static final String MANAGER_PREFIX = "manager:";

    private final KeyValueRepository<Manager> managerRepository;

    @Autowired
    public ManagerService(KeyValueRepository<Manager> managerRepository) {
        this.managerRepository = managerRepository;
    }

    public Manager createManager(Manager manager) {
        managerRepository.put(MANAGER_PREFIX + manager.getId(), manager);
        return manager;
    }

    public Optional<Manager> getManager(String id) {
        return managerRepository.get(MANAGER_PREFIX + id);
    }

    public List<Manager> getAllManagers() {
        return managerRepository.getAll(MANAGER_PREFIX);
    }

    public Manager updateManager(Manager manager) {
        managerRepository.put(MANAGER_PREFIX + manager.getId(), manager);
        return manager;
    }

    public void deleteManager(String id) {
        managerRepository.delete(MANAGER_PREFIX + id);
    }
}
