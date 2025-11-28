document.addEventListener('DOMContentLoaded', () => {
  const allAudioPlayers = []; // Array para mantener un registro de todos los elementos de audio
  let backgroundState = 'before'; // Para alternar entre ::before y ::after

  // Función global para actualizar el fondo del body
  function updateBodyBackground(colors) {
    const newGradient = (colors && colors.length === 2)
      ? `linear-gradient(135deg, ${colors[0]}, ${colors[1]})`
      : 'linear-gradient(135deg, #e0e0e0, #c0c0c0)';

    const beforeElement = document.body; // En JS, accedemos a los pseudo-elementos a través de su elemento padre
    const afterElement = document.body;

    if (backgroundState === 'before') {
      // El fondo actual está en ::before, el nuevo va a ::after
      afterElement.style.setProperty('--after-bg', newGradient); // Usamos una variable CSS para el fondo de ::after
      afterElement.style.setProperty('--after-opacity', '1');
      backgroundState = 'after';
    } else {
      // El fondo actual está en ::after, el nuevo va a ::before
      beforeElement.style.setProperty('--before-bg', newGradient);
      afterElement.style.setProperty('--after-opacity', '0');
      backgroundState = 'before';
    }
  }

  /**
   * Configura un reproductor de radio personalizado para una estación de Zeno.fm.
   * @param {object} config - La configuración para el reproductor.
   */
  function setupRadioPlayer(config) {
    const audioPlayer = document.getElementById(config.audioPlayerId);
    const playPauseBtn = document.getElementById(config.playPauseBtnId);
    const visualizerCanvas = document.getElementById(config.visualizerId);
    const volumeSlider = document.getElementById(config.volumeSliderId);

    const iconPlay = playPauseBtn.querySelector('.icon-play');
    const iconPause = playPauseBtn.querySelector('.icon-pause');
    let audioContext;
    let analyser;
    let source;
    let dataArray;

    allAudioPlayers.push(audioPlayer); // Añade este reproductor a la lista global

    function setupAudioContext() {
      if (!audioContext) {
        audioContext = new (window.AudioContext || window.webkitAudioContext)();
        analyser = audioContext.createAnalyser();
        source = audioContext.createMediaElementSource(audioPlayer);
        source.connect(analyser);
        analyser.connect(audioContext.destination);
        analyser.fftSize = 64;
        dataArray = new Uint8Array(analyser.frequencyBinCount);
      }
    }

    const volumeIcon = volumeSlider.parentElement.querySelector('.volume-icon');
    const volumeIconMuted = volumeSlider.parentElement.querySelector('.volume-icon-muted');

    volumeSlider.addEventListener('input', (e) => {
      audioPlayer.volume = e.target.value / 100;
    });

    playPauseBtn.addEventListener('click', () => {
      if (audioContext && audioContext.state === 'suspended') {
        audioContext.resume();
      }
      if (audioPlayer.paused) {
        // Pausa todos los demás reproductores antes de reproducir este
        allAudioPlayers.forEach(player => {
          if (player !== audioPlayer && !player.paused) {
            player.pause(); // Pausamos primero
            player.src = ''; // Vaciamos el src para detener el stream y el buffer
            player.load(); // Forzamos la descarga del stream

            // Forzar la actualización del icono del reproductor detenido
            const otherPlayerBtn = document.querySelector(`[data-player-id="${player.id}"]`);
            if (otherPlayerBtn) {
              otherPlayerBtn.querySelector('.icon-play').style.display = 'inline-block';
              otherPlayerBtn.querySelector('.icon-pause').style.display = 'none';
            }
          }
        });

        // Si el src de este reproductor está vacío o no coincide con la URL del stream,
        // lo restauramos y forzamos la recarga para obtener el stream en vivo.
        if (audioPlayer.src !== config.streamUrl) {
          audioPlayer.src = config.streamUrl;
          audioPlayer.load(); // Es crucial llamar a load() después de cambiar el src
        }

        setupAudioContext();
        const playPromise = audioPlayer.play();
        if (playPromise !== undefined) {
          playPromise.catch(error => console.error("Error al reproducir:", error));
        }
      } else {
        audioPlayer.pause();
      }
    });

    volumeIcon.addEventListener('click', () => toggleMute());
    volumeIconMuted.addEventListener('click', () => toggleMute());

    // Añadimos un atributo de datos para poder encontrar este botón más tarde
    playPauseBtn.dataset.playerId = config.audioPlayerId;

    function toggleMute() {
      audioPlayer.muted = !audioPlayer.muted;
      updateMuteIcon();
    }

    audioPlayer.addEventListener('play', () => {
      iconPlay.style.display = 'none';
      iconPause.style.display = 'inline-block';
      drawRealVisualizer();
      updateBodyBackground(config.gradientColors);
    });

    audioPlayer.addEventListener('pause', () => {
      iconPlay.style.display = 'inline-block';
      iconPause.style.display = 'none';
      // Si no hay ningún otro reproductor activo, vuelve al fondo por defecto
      const anyOtherPlayerPlaying = allAudioPlayers.some(player => player !== audioPlayer && !player.paused);
      if (!anyOtherPlayerPlaying) {
        updateBodyBackground(null); // Pasa null para activar el fondo por defecto
      }
    });

    audioPlayer.addEventListener('volumechange', () => {
      updateMuteIcon();
    });

    function drawRealVisualizer() {
      const ctx = visualizerCanvas.getContext('2d');

      function draw() {
        if (!audioPlayer || audioPlayer.paused) {
          ctx.clearRect(0, 0, visualizerCanvas.width, visualizerCanvas.height);
          return;
        }
        requestAnimationFrame(draw);
        analyser.getByteFrequencyData(dataArray);
        ctx.clearRect(0, 0, visualizerCanvas.width, visualizerCanvas.height);
        const barWidth = visualizerCanvas.width / analyser.frequencyBinCount;

        for (let i = 0; i < analyser.frequencyBinCount; i++) {
          const barHeight = (dataArray[i] / 255) * visualizerCanvas.height;
          ctx.fillStyle = config.visualizerColor;
          ctx.fillRect(i * barWidth, visualizerCanvas.height - barHeight, barWidth - 1, barHeight);
        }
      }
      draw();
    }

    function updateMuteIcon() {
      if (audioPlayer.muted || audioPlayer.volume === 0) {
        volumeIcon.style.display = 'none';
        volumeIconMuted.style.display = 'inline';
        if (audioPlayer.volume > 0) volumeSlider.value = 0;
      } else {
        volumeIcon.style.display = 'inline';
        volumeIconMuted.style.display = 'none';
        volumeSlider.value = audioPlayer.volume * 100;
      }
    }
  }

  // --- Configuración para Radio Transparencia ---
  setupRadioPlayer({
    audioPlayerId: 'audio-player-transparencia',
    playPauseBtnId: 'play-pause-btn-transparencia',
    visualizerId: 'visualizer-transparencia',
    volumeSliderId: 'volume-slider',
    streamUrl: 'https://stream.zeno.fm/a5tnl0xjodbvv',
    visualizerColor: '#0077cc',
    gradientColors: ['#0077cc', '#f09231'] // Azul y Naranja para Transparencia
  });

  // --- Configuración para Extasis Radio ---
  setupRadioPlayer({
    audioPlayerId: 'audio-player-extasis',
    playPauseBtnId: 'play-pause-btn-extasis',
    visualizerId: 'visualizer-extasis',
    volumeSliderId: 'volume-slider-extasis',
    streamUrl: 'https://stream.zeno.fm/cp63pctjeuuuv',
    visualizerColor: '#e4002b',
    gradientColors: ['#e4002b', '#fcc200'] // Rojo y Amarillo para Extasis
  });
});
