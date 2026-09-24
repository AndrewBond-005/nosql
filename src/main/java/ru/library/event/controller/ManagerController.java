package ru.library.event.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.library.event.model.Manager;
import ru.library.event.service.ManagerService;

import java.util.List;

@RestController
@RequestMapping("/api/managers")
@CrossOrigin(origins = "*")
public class ManagerController {

    private final ManagerService managerService;

    @Autowired
    public ManagerController(ManagerService managerService) {
        this.managerService = managerService;
    }

    @PostMapping
    public ResponseEntity<Manager> createManager(@RequestBody Manager manager) {
        return ResponseEntity.ok(managerService.createManager(manager));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Manager> getManager(@PathVariable String id) {
        return managerService.getManager(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<Manager>> getAllManagers() {
        return ResponseEntity.ok(managerService.getAllManagers());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Manager> updateManager(@PathVariable String id, @RequestBody Manager manager) {
        manager.setId(id);
        return ResponseEntity.ok(managerService.updateManager(manager));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteManager(@PathVariable String id) {
        managerService.deleteManager(id);
        return ResponseEntity.ok().build();
    }
}
