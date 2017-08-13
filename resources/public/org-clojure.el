(org-babel-do-load-languages
 'org-babel-load-languages
 '((clojure . t)
   (sh . t)
   (emacs-lisp . t)))

(setq org-babel-clojure-backend 'cider)


(setq org-edit-src-content-indentation 0
      org-src-tab-acts-natively t
      org-src-fontify-natively t
       org-confirm-babel-evaluate nil
       org-support-shift-select 'always)


(org-defkey org-mode-map "\C-x\C-e" 'cider-eval-last-sexp)


(setq org-hide-emphasis-markers t)


(add-hook 'org-mode-hook 'turn-on-visual-line-mode)


(defvar org-babel-clojure-nrepl-timeout nil)


(defun org-babel-execute:clojure (body params)
  "Execute a block of Clojure code with Babel."
  (lexical-let* ((expanded (org-babel-expand-body:clojure body params))
                 ; name of the buffer that will receive the asyn output
                 (sbuffer "*Clojure Sub Buffer*")
                 ; determine if the :async option is specified for this block
                 (async (if (assoc :async params) t nil))
                 ; generate the full response from the REPL
                 (response (cons 'dict nil))
                 ; keep track of the status of the output in async mode
                 status
                 ; result to return to Babel
                 result)
    (case org-babel-clojure-backend
      (cider
       (require 'cider)
       (let ((result-params (cdr (assoc :result-params params))))
         ; Check if the user want to run code asynchronously
         (when async
           ; Create a new window with the async output buffer
           (switch-to-buffer-other-window sbuffer)

           ; Run the Clojure code asynchronously in nREPL
           (nrepl-request:eval
            expanded
            (lambda (resp)
              (when (member "out" resp)
                ; Print the output of the nREPL in the asyn output buffer
                (princ (nrepl-dict-get resp "out") (get-buffer sbuffer)))
              (nrepl--merge response resp)
              ; Update the status of the nREPL output session
              (setq status (nrepl-dict-get response "status")))
            (cider-current-connection)
            (cider-current-session))

           ; Wait until the nREPL code finished to be processed
           (while (not (member "done" status))
             (nrepl-dict-put response "status" (remove "need-input" status))
             (accept-process-output nil 0.01)
             (redisplay))

           ; Delete the async buffer & window when the processing is finalized
           (let ((wins (get-buffer-window-list sbuffer nil t)))
             (dolist (win wins)
               (delete-window win))
             (kill-buffer sbuffer))

           ; Put the output or the value in the result section of the code block
           (setq result (nrepl-dict-get response
                                        (if (or (member "output" result-params)
                                                (member "pp" result-params))
                                            "out"
                                          "value"))))
         ; Check if user want to run code synchronously
         (when (not async)
           (setq result
                 (nrepl-dict-get
                  (let ((nrepl-sync-request-timeout
                         org-babel-clojure-nrepl-timeout))
                    (nrepl-sync-request:eval
                     expanded (cider-current-connection) (cider-current-session)))
                  (if (or (member "output" result-params)
                          (member "pp" result-params))
                      "out"
                    "value"))))))
      (slime
       (require 'slime)
       (with-temp-buffer
         (insert expanded)
         (setq result
               (slime-eval
                `(swank:eval-and-grab-output
                  ,(buffer-substring-no-properties (point-min) (point-max)))
                (cdr (assoc :package params)))))))
    (org-babel-result-cond (cdr (assoc :result-params params))
      result
      (condition-case nil (org-babel-script-escape result)
        (error result)))))

;;; http://fgiasson.com/blog/index.php/2016/04/05/using-clojure-in-org-mode-and-implementing-asynchronous-processing/
