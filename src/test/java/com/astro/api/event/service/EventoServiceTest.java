package com.astro.api.event.service;

import com.astro.api.cargo.model.Cargo;
import com.astro.api.cargo.repository.CargoRepository;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.conformidade.repository.NrCatalogRepository;
import com.astro.api.event.dto.request.CancelEventoRequest;
import com.astro.api.event.dto.request.CreateEventoRequest;
import com.astro.api.event.dto.request.CreateTurmaRequest;
import com.astro.api.event.model.Evento;
import com.astro.api.event.model.ModoConclusao;
import com.astro.api.event.model.StatusEvento;
import com.astro.api.event.model.Turma;
import com.astro.api.event.repository.EventoRepository;
import com.astro.api.event.repository.TurmaFuncionarioRepository;
import com.astro.api.event.repository.TurmaRepository;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.model.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EventoServiceTest {
    private EventoRepository eventoRepository;
    private TurmaRepository turmaRepository;
    private TurmaFuncionarioRepository turmaFuncionarioRepository;
    private UserRepository userRepository;
    private CargoRepository cargoRepository;
    private NrCatalogRepository nrCatalogRepository;
    private EventoService service;
    private User gestor;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        eventoRepository = mock(EventoRepository.class);
        turmaRepository = mock(TurmaRepository.class);
        turmaFuncionarioRepository = mock(TurmaFuncionarioRepository.class);
        userRepository = mock(UserRepository.class);
        cargoRepository = mock(CargoRepository.class);
        nrCatalogRepository = mock(NrCatalogRepository.class);
        service = new EventoService(eventoRepository, turmaRepository, turmaFuncionarioRepository, userRepository, cargoRepository, nrCatalogRepository);
        workspace = new Workspace(); workspace.id = 10L;
        gestor = usuario(1L, UserStatus.ATIVO, workspace); gestor.setFirebaseUid("gestor");
        when(userRepository.findByFirebaseUid("gestor")).thenReturn(Optional.of(gestor));
    }

    @Test
    void criaEventoSemNrUsandoOperacoesEmLote() {
        User participante = usuario(2L, UserStatus.ATIVO, workspace);
        when(userRepository.findAllById(any())).thenReturn(List.of(participante));
        when(eventoRepository.save(any(Evento.class))).thenAnswer(invocation -> { Evento evento = invocation.getArgument(0); evento.setId(11L); return evento; });
        when(turmaRepository.saveAll(any())).thenAnswer(invocation -> { List<Turma> turmas = invocation.getArgument(0); turmas.getFirst().setId(21L); return turmas; });
        when(turmaFuncionarioRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.criar(criacao(null, List.of(2L)), "gestor");

        assertEquals(StatusEvento.ATIVO, response.status());
        assertEquals(1, response.turmas().size());
        verify(userRepository).findAllById(any());
        verify(turmaRepository).saveAll(any());
        verify(turmaFuncionarioRepository).saveAll(any());
    }

    @Test
    void rejeitaEventoSemTurma() {
        CreateEventoRequest request = new CreateEventoRequest("Evento", null, null, null, ModoConclusao.GESTOR, false, List.of());
        assertThrows(BusinessException.class, () -> service.criar(request, "gestor"));
        verifyNoInteractions(eventoRepository, turmaRepository, turmaFuncionarioRepository);
    }

    @Test
    void rejeitaParticipanteRepetidoEmDuasTurmasAntesDeConsultarBanco() {
        LocalDateTime inicio = LocalDateTime.now().plusDays(1);
        CreateTurmaRequest primeira = new CreateTurmaRequest("A", inicio, inicio.plusHours(1), List.of(2L));
        CreateTurmaRequest segunda = new CreateTurmaRequest("B", inicio.plusDays(1), inicio.plusDays(1).plusHours(1), List.of(2L));
        CreateEventoRequest request = new CreateEventoRequest("Evento", null, null, null, ModoConclusao.GESTOR, false, List.of(primeira, segunda));

        assertThrows(BusinessException.class, () -> service.criar(request, "gestor"));
        verify(userRepository, never()).findAllById(any());
    }

    @Test
    void rejeitaParticipanteInativo() {
        User participante = usuario(2L, UserStatus.DESATIVADO, workspace);
        when(userRepository.findAllById(any())).thenReturn(List.of(participante));
        assertThrows(BusinessException.class, () -> service.criar(criacao(null, List.of(2L)), "gestor"));
        verifyNoInteractions(eventoRepository);
    }

    @Test
    void rejeitaGestorCriadorComoParticipante() {
        when(userRepository.findAllById(any())).thenReturn(List.of(gestor));
        assertThrows(BusinessException.class, () -> service.criar(criacao(null, List.of(1L)), "gestor"));
    }

    @Test
    void rejeitaParticipanteSemCargoElegivelParaNr() {
        User participante = usuario(2L, UserStatus.ATIVO, workspace);
        when(nrCatalogRepository.existsById(10)).thenReturn(true);
        when(userRepository.findAllById(any())).thenReturn(List.of(participante));
        when(cargoRepository.findIdsAssociatedWithNr(eq(10), any())).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> service.criar(criacao(10, List.of(2L)), "gestor"));
    }

    @Test
    void concluiAutomaticamenteQuandoUltimaTurmaTerminou() {
        Evento evento = evento(StatusEvento.ATIVO);
        Turma turma = turma(evento, LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1));
        when(eventoRepository.findByIdAndWorkspace(30L, workspace)).thenReturn(Optional.of(evento));
        when(turmaRepository.findByEventoOrderByDataInicial(evento)).thenReturn(List.of(turma));
        when(turmaFuncionarioRepository.findByTurmaIn(List.of(turma))).thenReturn(List.of());

        var resposta = service.buscar(30L, "gestor");

        assertEquals(StatusEvento.CONCLUIDO, resposta.status());
        verify(eventoRepository).save(evento);
    }

    @Test
    void cancelaDefinitivamenteEventoAtivo() {
        Evento evento = evento(StatusEvento.ATIVO);
        Turma turma = turma(evento, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1));
        when(eventoRepository.findByIdAndWorkspace(30L, workspace)).thenReturn(Optional.of(evento));
        when(turmaRepository.findByEventoOrderByDataInicial(evento)).thenReturn(List.of(turma));
        when(turmaFuncionarioRepository.findByTurmaIn(List.of(turma))).thenReturn(List.of());

        var resposta = service.cancelar(30L, new CancelEventoRequest("Motivo válido"), "gestor");

        assertEquals(StatusEvento.CANCELADO, resposta.status());
        assertNotNull(evento.getDataCancelamento());
        assertEquals("Motivo válido", evento.getMotivoCancelamento());
    }

    private CreateEventoRequest criacao(Integer nrId, List<Long> ids) {
        LocalDateTime inicio = LocalDateTime.now().plusDays(1);
        return new CreateEventoRequest("Evento", "Descrição", nrId, null, ModoConclusao.COLABORADOR, true,
                List.of(new CreateTurmaRequest("Turma", inicio, inicio.plusHours(2), ids)));
    }

    private User usuario(Long id, UserStatus status, Workspace owner) {
        Unit unidade = new Unit(); unidade.workspace = owner;
        Cargo cargo = new Cargo(); cargo.setId(id + 100); cargo.setWorkspace(owner); cargo.setName("Cargo");
        User usuario = new User(); usuario.setId(id); usuario.setName("Usuário " + id); usuario.setEmail("u" + id + "@astro.com"); usuario.setStatus(status); usuario.setUnit(unidade); usuario.setCargo(cargo);
        return usuario;
    }

    private Evento evento(StatusEvento status) {
        Evento evento = new Evento(); evento.setId(30L); evento.setWorkspace(workspace); evento.setGestor(gestor); evento.setStatus(status); evento.setTitulo("Evento"); evento.setModoConclusao(ModoConclusao.GESTOR); return evento;
    }

    private Turma turma(Evento evento, LocalDateTime inicio, LocalDateTime termino) {
        Turma turma = new Turma(); turma.setId(40L); turma.setEvento(evento); turma.setNome("Turma"); turma.setDataInicial(inicio); turma.setDataTermino(termino); return turma;
    }
}
