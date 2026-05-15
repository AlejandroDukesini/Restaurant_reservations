import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/mesas")
public class MesaController {

    private final MesaRepository repository;

    public MesaController(MesaRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public Mesa crear(@RequestBody Mesa mesa) {
        return repository.save(mesa);
    }

    @GetMapping
    public List<Mesa> listar() {
        return repository.findAll();
    }
}