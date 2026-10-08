package com.astro.api.event.service;

import com.astro.api.cargo.repository.CargoRepository;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.conformidade.repository.NrCatalogRepository;
import com.astro.api.event.dto.request.*;
import com.astro.api.event.dto.response.CandidatoEventoResponse;
import com.astro.api.event.dto.response.EventoResponse;
import com.astro.api.event.dto.response.ParticipanteEventoResponse;
import com.astro.api.event.dto.response.TurmaEventoResponse;
import com.astro.api.event.model.Evento;
import com.astro.api.event.model.StatusEvento;
import com.astro.api.event.model.Turma;
import com.astro.api.event.model.TurmaFuncionario;
import com.astro.api.event.repository.EventoRepository;
import com.astro.api.event.repository.TurmaFuncionarioRepository;
import com.astro.api.event.repository.TurmaRepository;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.model.Workspace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EventoService {
    private final EventoRepository eventoRepository;
    private final TurmaRepository turmaRepository;
    private final TurmaFuncionarioRepository turmaFuncionarioRepository;
    private final UserRepository userRepository;
    private final CargoRepository cargoRepository;
    private final NrCatalogRepository nrCatalogRepository;

    public EventoService(EventoRepository eventoRepository, TurmaRepository turmaRepository,
                         TurmaFuncionarioRepository turmaFuncionarioRepository, UserRepository userRepository,
                         CargoRepository cargoRepository, NrCatalogRepository nrCatalogRepository) {
        this.eventoRepository = eventoRepository;
        this.turmaRepository = turmaRepository;
        this.turmaFuncionarioRepository = turmaFuncionarioRepository;
        this.userRepository = userRepository;
        this.cargoRepository = cargoRepository;
        this.nrCatalogRepository = nrCatalogRepository;
    }

    @Transactional
    public EventoResponse criar(CreateEventoRequest request, String firebaseUid) {
        User gestor = gestorAutenticado(firebaseUid);
        Workspace workspace = workspaceDo(gestor);
        validarTurmasParaCriacao(request.turmas());
        if (request.nrId() != null && !nrCatalogRepository.existsById(request.nrId())) {
            throw new ResourceNotFoundException("NR não encontrada");
        }

        Set<Long> participantesIds = request.turmas().stream()
                .flatMap(turma -> turma.participantesIds().stream()).collect(Collectors.toSet());
        Map<Long, User> participantes = buscarEValidarParticipantes(participantesIds, workspace, gestor, request.nrId());

        Evento evento = new Evento();
        evento.setTitulo(request.titulo().trim());
        evento.setDescricao(request.descricao());
        evento.setNrId(request.nrId());
        evento.setLinkExterno(request.linkExterno());
        evento.setModoConclusao(request.modoConclusao());
        evento.setEvidenciaObrigatoria(request.evidenciaObrigatoria());
        evento.setStatus(StatusEvento.ATIVO);
        evento.setGestor(gestor);
        evento.setWorkspace(workspace);
        evento = eventoRepository.save(evento);

        Evento eventoSalvo = evento;
        List<Turma> turmas = request.turmas().stream().map(item -> novaTurma(eventoSalvo, item)).toList();
        turmas = turmaRepository.saveAll(turmas);

        List<TurmaFuncionario> participacoes = new ArrayList<>();
        for (int i = 0; i < turmas.size(); i++) {
            Turma turma = turmas.get(i);
            for (Long participanteId : request.turmas().get(i).participantesIds()) {
                TurmaFuncionario participacao = new TurmaFuncionario();
                participacao.setTurma(turma);
                participacao.setUsuario(participantes.get(participanteId));
                participacoes.add(participacao);
            }
        }
        turmaFuncionarioRepository.saveAll(participacoes);
        return paraResposta(evento, turmas, participacoes);
    }

    public EventoResponse buscar(Long eventoId, String firebaseUid) {
        Evento evento = eventoDoWorkspace(eventoId, workspaceDo(gestorAutenticado(firebaseUid)));
        return paraResposta(evento, turmasAtualizandoStatus(evento), null);
    }

    public List<EventoResponse> listar(LocalDateTime inicio, LocalDateTime fim, String firebaseUid) {
        Workspace workspace = workspaceDo(gestorAutenticado(firebaseUid));
        return eventoRepository.findByWorkspace(workspace).stream()
                .map(evento -> new AbstractMap.SimpleEntry<>(evento, turmasAtualizandoStatus(evento)))
                .filter(entry -> noPeriodo(entry.getValue(), inicio, fim))
                .map(entry -> paraResposta(entry.getKey(), entry.getValue(), null))
                .toList();
    }

    public List<CandidatoEventoResponse> candidatos(Long eventoId, String firebaseUid) {
        Workspace workspace = workspaceDo(gestorAutenticado(firebaseUid));
        Evento evento = eventoDoWorkspace(eventoId, workspace);
        garantirAlteravel(evento);
        List<User> usuarios = userRepository.findByUnit_WorkspaceAndStatus(workspace, UserStatus.ATIVO).stream()
                .filter(usuario -> !usuario.getId().equals(evento.getGestor().getId()))
                .toList();
        if (evento.getNrId() != null) {
            Set<Long> cargosElegiveis = cargosElegiveis(evento.getNrId(), usuarios);
            usuarios = usuarios.stream().filter(usuario -> usuario.getCargo() != null && cargosElegiveis.contains(usuario.getCargo().getId())).toList();
        }
        return usuarios.stream().map(usuario -> new CandidatoEventoResponse(usuario.getId(), usuario.getName(), usuario.getEmail(),
                usuario.getCargo() == null ? null : usuario.getCargo().getId(),
                usuario.getCargo() == null ? null : usuario.getCargo().getName())).toList();
    }

    public EventoResponse atualizarEvento(Long eventoId, UpdateEventoRequest request, String firebaseUid) {
        User gestor = gestorAutenticado(firebaseUid);
        Evento evento = eventoDoWorkspace(eventoId, workspaceDo(gestor));
        garantirResponsavelEAlteravel(evento, gestor);
        evento.setTitulo(request.titulo().trim());
        evento.setDescricao(request.descricao());
        evento.setLinkExterno(request.linkExterno());
        eventoRepository.save(evento);
        return paraResposta(evento, turmasAtualizandoStatus(evento), null);
    }

    public EventoResponse atualizarTurma(Long eventoId, Long turmaId, UpdateTurmaRequest request, String firebaseUid) {
        User gestor = gestorAutenticado(firebaseUid);
        Evento evento = eventoDoWorkspace(eventoId, workspaceDo(gestor));
        garantirResponsavelEAlteravel(evento, gestor);
        Turma turma = turmaDoEvento(turmaId, evento);
        garantirTurmaNaoIniciada(turma);
        validarDatas(request.dataInicial(), request.dataTermino());
        turma.setNome(request.nome().trim());
        turma.setDataInicial(request.dataInicial());
        turma.setDataTermino(request.dataTermino());
        turmaRepository.save(turma);
        return paraResposta(evento, turmasAtualizandoStatus(evento), null);
    }

    @Transactional
    public EventoResponse substituirParticipantes(Long eventoId, Long turmaId, UpdateParticipantesTurmaRequest request, String firebaseUid) {
        User gestor = gestorAutenticado(firebaseUid);
        Evento evento = eventoDoWorkspace(eventoId, workspaceDo(gestor));
        garantirResponsavelEAlteravel(evento, gestor);
        Turma turma = turmaDoEvento(turmaId, evento);
        garantirTurmaNaoIniciada(turma);
        Set<Long> novosIds = new LinkedHashSet<>(request.participantesIds());
        if (novosIds.size() != request.participantesIds().size()) throw new BusinessException("Participante repetido na turma");
        if (novosIds.isEmpty()) throw new BusinessException("Turma deve possuir ao menos um participante");

        List<Turma> outrasTurmas = turmaRepository.findByEventoOrderByDataInicial(evento).stream().filter(item -> !item.getId().equals(turmaId)).toList();
        Set<Long> idsEmOutrasTurmas = turmaFuncionarioRepository.findByTurmaIn(outrasTurmas).stream()
                .map(participacao -> participacao.getUsuario().getId()).collect(Collectors.toSet());
        if (novosIds.stream().anyMatch(idsEmOutrasTurmas::contains)) throw new BusinessException("Participante repetido em outra turma do evento");
        Map<Long, User> participantes = buscarEValidarParticipantes(novosIds, evento.getWorkspace(), gestor, evento.getNrId());
        turmaFuncionarioRepository.deleteAll(turmaFuncionarioRepository.findByTurma(turma));
        turmaFuncionarioRepository.saveAll(novosIds.stream().map(id -> participacao(turma, participantes.get(id))).toList());
        return paraResposta(evento, turmasAtualizandoStatus(evento), null);
    }

    @Transactional
    public EventoResponse cancelar(Long eventoId, CancelEventoRequest request, String firebaseUid) {
        User gestor = gestorAutenticado(firebaseUid);
        Evento evento = eventoDoWorkspace(eventoId, workspaceDo(gestor));
        garantirResponsavelEAlteravel(evento, gestor);
        evento.setStatus(StatusEvento.CANCELADO);
        evento.setDataCancelamento(Instant.now());
        evento.setMotivoCancelamento(request.motivo().trim());
        eventoRepository.save(evento);
        return paraResposta(evento, turmaRepository.findByEventoOrderByDataInicial(evento), null);
    }

    private void validarTurmasParaCriacao(List<CreateTurmaRequest> turmas) {
        if (turmas == null || turmas.isEmpty()) throw new BusinessException("Evento deve possuir ao menos uma turma");
        Set<Long> todos = new HashSet<>();
        for (CreateTurmaRequest turma : turmas) {
            if (turma.participantesIds() == null || turma.participantesIds().isEmpty()) throw new BusinessException("Turma deve possuir ao menos um participante");
            validarDatas(turma.dataInicial(), turma.dataTermino());
            Set<Long> daTurma = new HashSet<>(turma.participantesIds());
            if (daTurma.size() != turma.participantesIds().size()) throw new BusinessException("Participante repetido na turma");
            if (!todos.addAll(daTurma)) throw new BusinessException("Participante repetido em duas turmas do evento");
        }
    }

    private Map<Long, User> buscarEValidarParticipantes(Collection<Long> ids, Workspace workspace, User gestor, Integer nrId) {
        List<User> usuarios = userRepository.findAllById(ids);
        Map<Long, User> porId = usuarios.stream().collect(Collectors.toMap(User::getId, Function.identity()));
        if (porId.size() != ids.size()) throw new ResourceNotFoundException("Participante não encontrado");
        Set<Long> cargosElegiveis = nrId == null ? Set.of() : cargosElegiveis(nrId, usuarios);
        for (Long id : ids) {
            User usuario = porId.get(id);
            if (usuario.getStatus() != UserStatus.ATIVO) throw new BusinessException("Participante não está ATIVO");
            if (usuario.getUnit() == null || usuario.getUnit().workspace == null || !workspace.id.equals(usuario.getUnit().workspace.id)) throw new BusinessException("Participante pertence a outro Workspace");
            if (gestor.getId().equals(usuario.getId())) throw new BusinessException("Gestor criador não pode participar do próprio evento");
            if (nrId != null && (usuario.getCargo() == null || !cargosElegiveis.contains(usuario.getCargo().getId()))) throw new BusinessException("Participante não elegível para a NR");
        }
        return porId;
    }

    private Set<Long> cargosElegiveis(Integer nrId, Collection<User> usuarios) {
        List<Long> cargoIds = usuarios.stream().map(User::getCargo).filter(Objects::nonNull).map(cargo -> cargo.getId()).distinct().toList();
        return cargoIds.isEmpty() ? Set.of() : new HashSet<>(cargoRepository.findIdsAssociatedWithNr(nrId, cargoIds));
    }

    private User gestorAutenticado(String firebaseUid) {
        return userRepository.findByFirebaseUid(firebaseUid).orElseThrow(() -> new ResourceNotFoundException("Usuário autenticado não encontrado"));
    }

    private Workspace workspaceDo(User usuario) {
        if (usuario.getUnit() == null || usuario.getUnit().workspace == null) throw new BusinessException("Usuário autenticado não pertence a um Workspace");
        return usuario.getUnit().workspace;
    }

    private Evento eventoDoWorkspace(Long eventoId, Workspace workspace) {
        return eventoRepository.findByIdAndWorkspace(eventoId, workspace).orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado"));
    }

    private Turma turmaDoEvento(Long turmaId, Evento evento) {
        return turmaRepository.findByIdAndEvento(turmaId, evento).orElseThrow(() -> new ResourceNotFoundException("Turma não encontrada"));
    }

    private void garantirResponsavelEAlteravel(Evento evento, User gestor) {
        if (!evento.getGestor().getId().equals(gestor.getId())) throw new BusinessException("Operação não autorizada para este evento");
        garantirAlteravel(evento);
    }

    private void garantirAlteravel(Evento evento) {
        atualizarStatusSeNecessario(evento, turmaRepository.findByEventoOrderByDataInicial(evento));
        if (evento.getStatus() == StatusEvento.CANCELADO) throw new BusinessException("Evento cancelado não pode ser alterado");
        if (evento.getStatus() == StatusEvento.CONCLUIDO) throw new BusinessException("Evento concluído não pode ser alterado");
    }

    private void garantirTurmaNaoIniciada(Turma turma) {
        if (!LocalDateTime.now().isBefore(turma.getDataInicial())) throw new BusinessException("Turma já iniciada");
    }

    private void validarDatas(LocalDateTime inicial, LocalDateTime termino) {
        if (inicial == null || termino == null || !termino.isAfter(inicial)) throw new BusinessException("Data final deve ser posterior à data inicial");
    }

    private Turma novaTurma(Evento evento, CreateTurmaRequest item) {
        Turma turma = new Turma();
        turma.setEvento(evento); turma.setNome(item.nome().trim()); turma.setDataInicial(item.dataInicial()); turma.setDataTermino(item.dataTermino());
        return turma;
    }

    private TurmaFuncionario participacao(Turma turma, User usuario) {
        TurmaFuncionario item = new TurmaFuncionario(); item.setTurma(turma); item.setUsuario(usuario); return item;
    }

    private List<Turma> turmasAtualizandoStatus(Evento evento) {
        List<Turma> turmas = turmaRepository.findByEventoOrderByDataInicial(evento);
        atualizarStatusSeNecessario(evento, turmas);
        return turmas;
    }

    private void atualizarStatusSeNecessario(Evento evento, List<Turma> turmas) {
        if (evento.getStatus() != StatusEvento.ATIVO || turmas.isEmpty()) return;
        LocalDateTime ultimaData = turmas.stream().map(Turma::getDataTermino).max(LocalDateTime::compareTo).orElseThrow();
        if (LocalDateTime.now().isAfter(ultimaData)) { evento.setStatus(StatusEvento.CONCLUIDO); eventoRepository.save(evento); }
    }

    private boolean noPeriodo(List<Turma> turmas, LocalDateTime inicio, LocalDateTime fim) {
        return turmas.stream().anyMatch(turma -> (inicio == null || !turma.getDataTermino().isBefore(inicio)) && (fim == null || !turma.getDataInicial().isAfter(fim)));
    }

    private EventoResponse paraResposta(Evento evento, List<Turma> turmas, List<TurmaFuncionario> participacoesJaCarregadas) {
        List<TurmaFuncionario> participacoes = participacoesJaCarregadas == null ? turmaFuncionarioRepository.findByTurmaIn(turmas) : participacoesJaCarregadas;
        Map<Long, List<ParticipanteEventoResponse>> porTurma = participacoes.stream().collect(Collectors.groupingBy(item -> item.getTurma().getId(), Collectors.mapping(item -> new ParticipanteEventoResponse(item.getUsuario().getId(), item.getUsuario().getName(), item.getUsuario().getEmail()), Collectors.toList())));
        List<TurmaEventoResponse> respostaTurmas = turmas.stream().map(turma -> new TurmaEventoResponse(turma.getId(), turma.getNome(), turma.getDataInicial(), turma.getDataTermino(), porTurma.getOrDefault(turma.getId(), List.of()))).toList();
        return new EventoResponse(evento.getId(), evento.getTitulo(), evento.getDescricao(), evento.getNrId(), evento.getLinkExterno(), evento.getModoConclusao(), evento.isEvidenciaObrigatoria(), evento.getStatus(), evento.getDataCancelamento(), evento.getMotivoCancelamento(), respostaTurmas);
    }
}
