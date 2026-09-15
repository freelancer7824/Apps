document.addEventListener("DOMContentLoaded", () => {
  // 1. Connectivity Detection
  const statusBadge = document.getElementById("statusBadge");
  const statusText = document.getElementById("statusText");

  function updateOnlineStatus() {
    if (navigator.onLine) {
      statusBadge.className = "badge";
      statusText.textContent = "Online";
    } else {
      statusBadge.className = "badge offline";
      statusText.textContent = "Offline Mode";
    }
  }

  window.addEventListener("online", updateOnlineStatus);
  window.addEventListener("offline", updateOnlineStatus);
  updateOnlineStatus();

  // 2. Camera Capture Handling
  const btnCamera = document.getElementById("btnCamera");
  const cameraInput = document.getElementById("cameraInput");
  const previewImage = document.getElementById("previewImage");

  if (btnCamera && cameraInput) {
    btnCamera.addEventListener("click", () => cameraInput.click());
    cameraInput.addEventListener("change", (e) => {
      const file = e.target.files[0];
      if (file) {
        const reader = new FileReader();
        reader.onload = (event) => {
          previewImage.src = event.target.result;
          previewImage.style.display = "block";
        };
        reader.readAsDataURL(file);
      }
    });
  }

  // 3. Multi-file Chooser Handling
  const btnFile = document.getElementById("btnFile");
  const fileInput = document.getElementById("fileInput");
  const fileInfo = document.getElementById("fileInfo");

  if (btnFile && fileInput) {
    btnFile.addEventListener("click", () => fileInput.click());
    fileInput.addEventListener("change", (e) => {
      const files = e.target.files;
      if (files && files.length > 0) {
        const names = Array.from(files).map(f => `${f.name} (${Math.round(f.size / 1024)} KB)`).join(", ");
        fileInfo.style.display = "block";
        fileInfo.textContent = `Selected ${files.length} file(s): ${names}`;
      }
    });
  }

  // 4. Geolocation API Handling
  const btnLocation = document.getElementById("btnLocation");
  const locationInfo = document.getElementById("locationInfo");

  if (btnLocation) {
    btnLocation.addEventListener("click", () => {
      if (!navigator.geolocation) {
        locationInfo.style.display = "block";
        locationInfo.textContent = "Geolocation is not supported by your device.";
        return;
      }
      locationInfo.style.display = "block";
      locationInfo.textContent = "Requesting GPS coordinates...";

      navigator.geolocation.getCurrentPosition(
        (position) => {
          const { latitude, longitude, accuracy } = position.coords;
          locationInfo.textContent = `Lat: ${latitude.toFixed(5)}, Lon: ${longitude.toFixed(5)} (±${Math.round(accuracy)}m)`;
        },
        (error) => {
          locationInfo.textContent = `Location Error: ${error.message} (code ${error.code})`;
        },
        { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
      );
    });
  }

  // 5. JavaScript Dialogs
  const btnAlert = document.getElementById("btnAlert");
  const btnConfirm = document.getElementById("btnConfirm");
  const btnPrompt = document.getElementById("btnPrompt");
  const dialogResult = document.getElementById("dialogResult");

  if (btnAlert) {
    btnAlert.addEventListener("click", () => {
      alert("Native WebChromeClient alert dialog working seamlessly!");
    });
  }

  if (btnConfirm) {
    btnConfirm.addEventListener("click", () => {
      const confirmed = confirm("Do you want to confirm this native dialog action?");
      dialogResult.style.display = "block";
      dialogResult.textContent = `Confirm result: ${confirmed ? "Accepted (true)" : "Canceled (false)"}`;
    });
  }

  if (btnPrompt) {
    btnPrompt.addEventListener("click", () => {
      const userText = prompt("Enter a greeting message:", "Hello from HTML5!");
      dialogResult.style.display = "block";
      dialogResult.textContent = `Prompt result: "${userText !== null ? userText : "Cancelled"}"`;
    });
  }

  // 6. Local Storage Counter Persistence
  const counterValue = document.getElementById("counterValue");
  const btnMinus = document.getElementById("btnMinus");
  const btnPlus = document.getElementById("btnPlus");
  let count = parseInt(localStorage.getItem("app_counter") || "0", 10);

  function renderCount() {
    if (counterValue) counterValue.textContent = count;
    localStorage.setItem("app_counter", count.toString());
  }
  renderCount();

  if (btnMinus) {
    btnMinus.addEventListener("click", () => {
      count--;
      renderCount();
    });
  }

  if (btnPlus) {
    btnPlus.addEventListener("click", () => {
      count++;
      renderCount();
    });
  }

  // 7. Local JSON Fetch
  const btnFetchJson = document.getElementById("btnFetchJson");
  const jsonOutput = document.getElementById("jsonOutput");

  if (btnFetchJson && jsonOutput) {
    btnFetchJson.addEventListener("click", () => {
      jsonOutput.style.display = "block";
      jsonOutput.textContent = "Loading local JSON...";
      fetch("data/sample.json")
        .then(response => {
          if (!response.ok) throw new Error("HTTP status " + response.status);
          return response.json();
        })
        .then(data => {
          jsonOutput.textContent = JSON.stringify(data, null, 2);
        })
        .catch(err => {
          jsonOutput.textContent = "JSON Load Error: " + err.message;
        });
    });
  }

  // 8. Web Audio API Synthesizer (Works completely offline without external media files)
  const btnSound = document.getElementById("btnSound");
  if (btnSound) {
    btnSound.addEventListener("click", () => {
      try {
        const AudioContext = window.AudioContext || window.webkitAudioContext;
        if (!AudioContext) return;
        const ctx = new AudioContext();
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();

        osc.type = "sine";
        osc.frequency.setValueAtTime(587.33, ctx.currentTime); // D5
        osc.frequency.exponentialRampToValueAtTime(880, ctx.currentTime + 0.15); // A5

        gain.gain.setValueAtTime(0.3, ctx.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.3);

        osc.connect(gain);
        gain.connect(ctx.destination);

        osc.start();
        osc.stop(ctx.currentTime + 0.3);
      } catch (e) {
        console.error("Audio error:", e);
      }
    });
  }
});
